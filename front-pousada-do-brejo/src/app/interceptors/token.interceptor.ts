// src/app/interceptors/token.interceptor.ts
import { Injectable } from '@angular/core';
import {
  HttpRequest, HttpHandler, HttpEvent, HttpInterceptor,
  HttpErrorResponse, HttpResponse, HTTP_INTERCEPTORS
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { Router } from '@angular/router';
import { AuthService } from '../services/login/auth.service';
import { ToastService } from '../toast/toast.service';

@Injectable()
export class TokenInterceptor implements HttpInterceptor {
  private toastCooldowns = new Set<string>();
  private handling401 = false;

  // 👉 rotas públicas (não exigir login e não redirecionar nelas)
  private readonly PUBLIC_PATHS = ['/login', '/nao-encontrada', '/acesso-negado'];

  constructor(
    private auth: AuthService,
    private router: Router,
    private toast: ToastService
  ) {}

  private isApi(url: string): boolean {
    try {
      const u = new URL(url, window.location.origin);
      return u.pathname.startsWith('/api') || /\/api(\/|$)/.test(u.pathname);
    } catch {
      return /^\/api(\/|$)/.test(url);
    }
  }

  private normalizeToken(raw: string | null | undefined): string {
    if (!raw) return '';
    let t = String(raw).trim();
    if (t.startsWith('"') && t.endsWith('"')) t = t.slice(1, -1);
    t = t.replace(/^Bearer\s+/i, '').trim();
    return t;
  }

  private showOnce(key: string, type: 'success' | 'error' | 'warning' | 'info', msg: string, duration = 4000) {
    if (this.toastCooldowns.has(key)) return;
    this.toastCooldowns.add(key);
    (this.toast as any)[type](msg, duration);
    setTimeout(() => this.toastCooldowns.delete(key), 5000);
  }

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const isLoginEndpoint = request.url.includes('/auth/login');
    const isApiRequest = this.isApi(request.url);

    // snapshot antes da requisição para decidir o que fazer no 401
    const hadToken = !!this.auth.getToken?.();

    if (!isLoginEndpoint && isApiRequest) {
      const token = this.normalizeToken(this.auth.getToken?.());
      if (token && !request.headers.has('Authorization')) {
        request = request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
      }
    }

    return next.handle(request).pipe(
      tap(event => {
        if (event instanceof HttpResponse && isApiRequest) {
          const body = event.body as any;
          if (body?.forceLogout === true) {
            const alvoId = body?.alvoId != null ? Number(body.alvoId) : null;
            const adminIdRaw = this.auth.getUsuarioIdLogado?.();
            const adminId = adminIdRaw != null ? Number(adminIdRaw) : null;

            if (adminId === null) {
              this.auth.logout();
              this.showOnce('forceLogout-admin-null', 'error', '🚫 Falha crítica de sessão. Faça login novamente.', 6000);
              return;
            }
            if (alvoId !== null && !isNaN(alvoId) && alvoId !== adminId) return;

            this.auth.logout();
            this.showOnce('forceLogout', 'error', '🚫 Sua sessão foi encerrada por ação administrativa. Faça login novamente.', 6000);
          }
        }
      }),
      catchError((error: HttpErrorResponse) => {
        if (!isApiRequest) return throwError(() => error);

        const currentUrl = this.router.url || '/';
        const onPublic = this.PUBLIC_PATHS.some(p => currentUrl.startsWith(p));
        const msg = (error?.error?.error || error?.error?.message || '').toString().toLowerCase();

        if (error.status === 0) {
          if (!onPublic) this.showOnce('network', 'error', 'Sem conexão com o servidor. Verifique sua internet.');
          return throwError(() => error);
        }

        switch (error.status) {
          case 401: {
            // 🔑 SEM token (usuário deslogado)
            if (!hadToken) {
              // ✅ Se já está numa rota pública (ex.: **/nao-encontrada**) → NÃO redireciona, NÃO mostra toast
              if (!onPublic) {
                // vindo de rota protegida → manda para login mas sem toast
                this.router.navigate(['/login'], { queryParams: { returnUrl: currentUrl } });
              }
              return throwError(() => error);
            }

            // 🔒 COM token (sessão expirada)
            if (!this.handling401 && !onPublic) {
              this.handling401 = true;
              this.auth.logout();
              this.showOnce('session-401', 'error', '⏰ Sessão expirada! Faça login novamente.', 6000);
              this.router.navigate(['/login'], { queryParams: { returnUrl: currentUrl } });
              setTimeout(() => (this.handling401 = false), 2000);
            }
            break;
          }
          case 403: {
            if (msg.includes('inativa') || msg.includes('bloqueada')) {
              this.auth.logout();
              if (!onPublic) this.showOnce('account-blocked', 'error', '❌ Conta bloqueada. Contate o administrador.');
              this.router.navigate(['/login']);
            } else {
              if (!onPublic) this.toast.warning('Você não tem permissão para essa ação.');
              this.router.navigate(['/acesso-negado']);
            }
            break;
          }
          default: {
            if (!onPublic) this.toast.error('Ocorreu um erro inesperado.');
            break;
          }
        }

        return throwError(() => error);
      })
    );
  }
}

export const TOKEN_INTERCEPTOR_PROVIDER = {
  provide: HTTP_INTERCEPTORS,
  useClass: TokenInterceptor,
  multi: true,
};
