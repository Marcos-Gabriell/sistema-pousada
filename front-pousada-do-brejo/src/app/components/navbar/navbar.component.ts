import {
  Component,
  OnInit,
  OnDestroy,
  Inject,
  Renderer2,
  Output,
  EventEmitter,
} from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule, DOCUMENT } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { ThemeService } from '../../services/theme.service';
import { UsersService } from '../../pages/users/users.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css'],
})
export class NavbarComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();


  private _sidebarOpen = false;
  get sidebarOpen(): boolean {
    return this._sidebarOpen;
  }
  set sidebarOpen(value: boolean) {
    this._sidebarOpen = value;
    this.toggleBodyScroll(value);
  }

  isAdmin = false;
  temaAtual: 'claro' | 'escuro' = 'claro';
  versao = '1.0.6';

  isCollapsedDesktop = false;

  @Output() sidebarCollapsedChange = new EventEmitter<boolean>();

  constructor(
    private router: Router,
    private themeService: ThemeService,
    private userService: UsersService,
    private renderer: Renderer2,
    @Inject(DOCUMENT) private document: Document
  ) {
    // Fecha a sidebar ao navegar para uma nova rota
    this.router.events.subscribe(() => (this.sidebarOpen = false));
  }

  ngOnInit(): void {
    const role = this.getRoleFromStorageOrToken();
    this.isAdmin = role === 'ADMIN' || role === 'DEV';

    // Escuta mudanças no tema
    this.themeService.tema$
      .pipe(takeUntil(this.destroy$))
      .subscribe((tema) => {
        this.temaAtual = tema;
      });

    // Carrega dados do usuário e tema
    this.userService.me().subscribe({
      next: (user) => {
        if (user?.id) {
          this.themeService.initFromUser(user.id, user.tema || 'claro');
        }
      },
      error: () => {
        console.warn('Não foi possível carregar o tema do usuário.');
      },
    });
  }

  alternarTema(): void {
    this.themeService.toggle();
  }

  toggleDesktopCollapse(): void {
    this.isCollapsedDesktop = !this.isCollapsedDesktop;
    this.sidebarCollapsedChange.emit(this.isCollapsedDesktop);
  }

  // trava/destrava o scroll do body quando o menu mobile estiver aberto
  private toggleBodyScroll(isOpen: boolean): void {
    if (isOpen) {
      this.renderer.setStyle(this.document.body, 'overflow', 'hidden');
      this.renderer.setStyle(this.document.body, 'touch-action', 'none');
    } else {
      this.renderer.removeStyle(this.document.body, 'overflow');
      this.renderer.removeStyle(this.document.body, 'touch-action');
    }
  }

  private getRoleFromStorageOrToken():
    | 'DEV'
    | 'ADMIN'
    | 'GERENTE'
    | null {
    try {
      const raw = localStorage.getItem('current_user');
      if (raw) {
        const u = JSON.parse(raw);
        const r = String(
          u?.role || (u?.roles?.[0] ?? '')
        ).toUpperCase();
        if (r === 'DEV' || r === 'ADMIN' || r === 'GERENTE') {
          return r as any;
        }
      }
    } catch {
      // ignore
    }

    const token = localStorage.getItem('token');
    if (!token) return null;

    try {
      const [, payloadB64] = token.split('.');
      const payload = JSON.parse(
        atob(payloadB64.replace(/-/g, '+').replace(/_/g, '/'))
      );

      let r: any =
        payload?.role ??
        (Array.isArray(payload?.authorities)
          ? payload.authorities[0]
          : null) ??
        (Array.isArray(payload?.roles) ? payload.roles[0] : null);

      if (typeof r === 'string') {
        r = r.replace(/^ROLE_/i, '').toUpperCase();
      }

      return r === 'DEV' || r === 'ADMIN' || r === 'GERENTE'
        ? r
        : null;
    } catch {
      return null;
    }
  }

  ngOnDestroy(): void {
    // garante que o body não fique travado se o componente for destruído
    this.toggleBodyScroll(false);
    this.destroy$.next();
    this.destroy$.complete();
  }
}
