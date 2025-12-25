import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { tap, Observable } from 'rxjs';
import { jwtDecode } from 'jwt-decode';
import { Router } from '@angular/router';
import { ToastService } from '../../toast/toast.service';


export type Role = 'DEV' | 'ADMIN' | 'GERENTE';
export type PwdReason = 'FIRST_LOGIN' | 'RESET_BY_ADMIN' | 'ACCOUNT_INACTIVATED' | 'UNKNOWN';

type JwtPayload = {
  exp?: number;
  roles?: string[];
  authorities?: string[];
  perfis?: string[];
  scope?: string;
  role?: string;
  sub?: string;
  [k: string]: any;
};

export interface CurrentUser {
  id?: number | string;
  nome?: string;
  email?: string;
  username?: string;
  numero?: string;
  roles?: Role[];
  role?: string;
  avatarUrl?: string;
  mustChangePassword?: boolean | null;
  pwdChangeReason?: PwdReason | string;
}

type LoginResponse = {
  token: string;
  role?: string;
  username?: string;
  email?: string;
  numero?: string;
  nome?: string;
  mustChangePassword?: boolean;
  pwdChangeReason?: PwdReason | string;
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiBase  = 'http://localhost:8080/api';
  private loginUrl = `${this.apiBase}/auth/login`;

  private readonly TOKEN_KEY       = 'token';
  private readonly USER_KEY        = 'current_user';
  private readonly MUST_CHANGE_KEY = 'must_change_pwd';
  user = signal<CurrentUser | null>(this.readUserFromStorage());

  constructor(
    private http: HttpClient,
    private router: Router,
    private toast: ToastService
  ) {
    this.ensureUserIdOnLoad();
  }

  private ensureUserIdOnLoad(): void {
    const currentUser = this.user();
    if (currentUser && !currentUser.id) {
      const payload = this.decode();
      if (payload?.sub) {
        const id = isNaN(Number(payload.sub)) ? payload.sub : Number(payload.sub);
        this.setCurrentUser({ ...currentUser, id });
      }
    }
  }

  private readUserFromStorage(): CurrentUser | null {
    try { return JSON.parse(localStorage.getItem(this.USER_KEY) || 'null'); }
    catch { return null; }
  }

  private normalizeToken(raw: string | null | undefined): string {
    if (!raw) return '';
    let t = String(raw).trim();
    if (t.startsWith('"') && t.endsWith('"')) t = t.slice(1, -1);
    t = t.replace(/^Bearer\s+/i, '').trim();
    return t;
  }

  private normalizeReason(r: any): PwdReason {
    const v = String(r || 'UNKNOWN').toUpperCase();
    return (['FIRST_LOGIN','RESET_BY_ADMIN','ACCOUNT_INACTIVATED'] as const).includes(v as any)
      ? (v as PwdReason)
      : 'UNKNOWN';
  }

  login(usuario: string, senha: string): Observable<LoginResponse> {
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    const body = { login: usuario, password: senha };

    return this.http.post<LoginResponse>(this.loginUrl, body, { headers }).pipe(
      tap((res) => {
        if (res?.token) {
          localStorage.setItem(this.TOKEN_KEY, this.normalizeToken(res.token));
        }

        const payload = this.decode();
        const idFromToken = payload?.sub;

        const roleUpper = (res?.role ? String(res.role).toUpperCase() : '') as Role | '';

        const user: CurrentUser = {
          id: idFromToken ? (isNaN(Number(idFromToken)) ? idFromToken : Number(idFromToken)) : undefined,
          nome: res?.nome ?? '',
          email: res?.email ?? '',
          username: res?.username ?? '',
          numero: res?.numero ?? '',
          roles: roleUpper ? [roleUpper] : undefined, // ⬅️ armazena como array
          role: roleUpper || undefined,
          mustChangePassword: !!res?.mustChangePassword,
          pwdChangeReason: res?.pwdChangeReason,
        };

        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
        this.user.set(user);

        if (res?.mustChangePassword) {
          const reason = this.normalizeReason(res?.pwdChangeReason);
          this.saveMustChangeFlag(reason);
          if (reason !== 'ACCOUNT_INACTIVATED') {
            this.toast.warning('🔐 Sua senha é temporária. Altere agora para maior segurança.');
          }
        } else {
          localStorage.removeItem(this.MUST_CHANGE_KEY);
        }
      })
    );
  }

  loadUserFromBackend(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${this.apiBase}/usuarios/me`).pipe(
      tap((user) => {
        if (!user || user.nome === undefined) return;

        if (!user.id) {
          const payload = this.decode();
          if (payload?.sub) {
            user.id = isNaN(Number(payload.sub)) ? payload.sub : Number(payload.sub);
          }
        }

        const r = (user as any)?.role ? String((user as any).role).toUpperCase() : '';
        if (r) user.roles = [r as Role];

        localStorage.setItem(this.USER_KEY, JSON.stringify(user || {}));
        this.user.set(user || null);

        if (user?.mustChangePassword) {
          const reason = this.normalizeReason((user as any)?.pwdChangeReason);
          this.saveMustChangeFlag(reason);

          if (reason === 'ACCOUNT_INACTIVATED') {
            this.logout();
            this.toast.error('❌ Sua conta foi desativada pelo administrador.');
          } else if (reason !== 'UNKNOWN') {
            this.toast.warning('🔐 Sua senha é temporária. Altere agora para maior segurança.');
          }
        } else {
          localStorage.removeItem(this.MUST_CHANGE_KEY);
        }
      })
    );
  }

  getCurrentUser(): CurrentUser | null { return this.user(); }
  public getUsuarioIdLogado(): number | string | null | undefined { return this.user()?.id; }

  setCurrentUser(user: CurrentUser | null) {
    this.user.set(user);
    if (!user) localStorage.removeItem(this.USER_KEY);
    else localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  updateLocalUserName(nome: string) {
    const u = this.user();
    if (!u) return;
    const updated = { ...u, nome };
    this.user.set(updated);
    localStorage.setItem(this.USER_KEY, JSON.stringify(updated));
  }


  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    localStorage.removeItem(this.MUST_CHANGE_KEY);
    this.user.set(null);
    this.router.navigate(['/login']);
  }

  getToken(): string { return this.normalizeToken(localStorage.getItem(this.TOKEN_KEY)); }

  private decode(): JwtPayload | null {
    const t = this.getToken();
    if (!t) return null;
    try { return jwtDecode<JwtPayload>(t); } catch { return null; }
  }

  isTokenValid(): boolean {
    const p = this.decode();
    if (!p) return false;
    if (!p.exp) return true;
    const now = Math.floor(Date.now() / 1000);
    return p.exp > now;
  }

  getRoles(): Role[] {
    const snap = this.user()?.roles;
    if (snap?.length) return snap;

    const p = this.decode();
    if (!p) return [];

    let raw: string[] = [];
    if (Array.isArray(p.roles)) raw = p.roles;
    else if (Array.isArray(p.authorities)) raw = p.authorities;
    else if (Array.isArray(p.perfis)) raw = p.perfis;
    else if (typeof p.scope === 'string') raw = p.scope.split(/\s+/);
    else if (typeof p.role === 'string') raw = [p.role];

    const roles = raw
      .map((r) => String(r).replace(/^ROLE_/i, '').toUpperCase())
      .filter((r) => r === 'DEV' || r === 'ADMIN' || r === 'GERENTE') as Role[];

    return Array.from(new Set(roles));
  }

  getPrimaryRole(): Role {
    const roles = this.getRoles();
    if (roles.includes('DEV')) return 'DEV';
    if (roles.includes('ADMIN')) return 'ADMIN';
    return 'GERENTE';
  }

  private saveMustChangeFlag(reason: PwdReason) {
    localStorage.setItem(this.MUST_CHANGE_KEY, JSON.stringify({ reason, ts: Date.now() }));
  }

  shouldPromptPasswordChange(): boolean {
    try { return !!JSON.parse(localStorage.getItem(this.MUST_CHANGE_KEY) || 'null'); }
    catch { return false; }
  }

  getPwdChangeReason(): PwdReason {
    try {
      const o = JSON.parse(localStorage.getItem(this.MUST_CHANGE_KEY) || 'null');
      return this.normalizeReason(o?.reason);
    } catch {
      return 'UNKNOWN';
    }
  }

  clearPromptPasswordChangeFlag() {
    localStorage.removeItem(this.MUST_CHANGE_KEY);
  }

  isDev()   { return this.getRoles().includes('DEV'); }
  isAdmin() { return this.getRoles().includes('ADMIN'); }

  public updateCurrentUser(partial: Partial<any>): void {
    try {
      if (this.user && typeof this.user === 'function' && (this.user as any).set) {
        const cur = (this.user as any)();
        if (!cur) return;
        (this.user as any).set({ ...cur, ...partial });
        return;
      }
      const maybe$ = (this as any)._user$ || (this as any).user$;
      if (maybe$ && typeof maybe$.next === 'function') {
        const cur = maybe$.value ?? null;
        if (!cur) return;
        maybe$.next({ ...cur, ...partial });
        return;
      }
    } catch {
    }
  }
}
