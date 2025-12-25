import { Component, OnDestroy, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';

import { filter, takeUntil } from 'rxjs/operators';
import { Subject } from 'rxjs';

import { NavbarComponent } from './components/navbar/navbar.component';
import { UiToastContainerComponent } from './toast/ui-toast-container.component';
import { UserMenuComponent } from './pages/user-menu/user-menu.component';
import { ForcePasswordChangeModalComponent } from './components/account/force-password-change-modal.component';
import { PasswordPromptService } from './services/login/password-prompt.service';
import { AuthService } from './services/login/auth.service';
import { NotificationsService } from './services/notifications/notifications.service';

import { NotificationsModalComponent } from './components/notifications/notifications-modal.component';
import { PerfilDadosModalComponent } from './components/perfil/Dados/perfil-dados-modal.component';
import { PerfilSenhaModalComponent } from './components/perfil/senha/perfil-senha-modal.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    NavbarComponent,
    UiToastContainerComponent,
    UserMenuComponent,
    ForcePasswordChangeModalComponent,
    NotificationsModalComponent,
    PerfilDadosModalComponent,
    PerfilSenhaModalComponent,
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
})
export class AppComponent implements OnDestroy {
  mostrarNavbar = true;

  sidebarCollapsed = false;

  private isInitialized = false;

  notificacoesModalOpen = signal(false);
  perfilModalOpen = signal(false);
  senhaModalOpen = signal(false);

  get modalOpen$() {
    return this.prompt.open$;
  }

  private destroy$ = new Subject<void>();

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  public  prompt = inject(PasswordPromptService);
  private auth   = inject(AuthService);
  public  notifications = inject(NotificationsService);

  constructor() {
    this.notifications.setEnabled(false);

    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntil(this.destroy$)
      )
      .subscribe((event) => {
        const url = event.urlAfterRedirects || event.url || '';

        const hideChrome = this.getDeepestData(this.route)?.['hideChrome'] === true;

        const isLoginPage = url.startsWith('/login');

        this.mostrarNavbar = !(hideChrome || isLoginPage);

        if (!this.mostrarNavbar) {
          this.notifications.setEnabled(false);
          this.notifications.disconnectSse();
          this.notifications.stopPolling();
          this.isInitialized = false;
          this.safeCloseAllModals();
        } else if (this.auth.getToken?.() && !this.isInitialized) {
          this.notifications.setEnabled(true);
          this.initializeServices();
          this.isInitialized = true;
        }
        if (this.mostrarNavbar && this.auth.shouldPromptPasswordChange?.()) {
          const reason = this.auth.getPwdChangeReason?.() || 'UNKNOWN';
          queueMicrotask(() => this.prompt.openWith(reason));
        }
        try {
          window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
        } catch {
          window.scrollTo(0, 0);
        }
      });
  }

  private getDeepestData(r: ActivatedRoute): any {
    let cur: ActivatedRoute | null = r;
    while (cur?.firstChild) cur = cur.firstChild;
    return cur?.snapshot?.data ?? {};
  }

  private initializeServices() {
    try {
      this.auth.loadUserFromBackend?.().subscribe({
        error: (err) => console.warn('Erro ao carregar usuário:', err),
      });

      this.notifications.initRealtime();
      this.notifications.refreshUnread();
    } catch (err) {
      console.warn('Erro ao inicializar serviços:', err);
    }
  }

  handleOpenNotifications() {
    if (!this.mostrarNavbar || this.router.url.startsWith('/login') || !this.auth.getToken?.())
      return;
    this.notificacoesModalOpen.set(true);
    this.notifications.refreshUnread();
  }
  handleCloseNotifications() {
    this.notificacoesModalOpen.set(false);
  }

  handleOpenProfile() {
    if (!this.mostrarNavbar) return;
    this.perfilModalOpen.set(true);
  }
  handleCloseProfile() {
    this.perfilModalOpen.set(false);
  }

  handleOpenPassword() {
    if (!this.mostrarNavbar) return;
    this.senhaModalOpen.set(true);
  }
  handleClosePassword() {
    this.senhaModalOpen.set(false);
  }

  handleSidebarCollapsedChange(collapsed: boolean) {
    this.sidebarCollapsed = collapsed;
  }

  private safeCloseAllModals() {
    this.notificacoesModalOpen.set(false);
    this.perfilModalOpen.set(false);
    this.senhaModalOpen.set(false);
    this.prompt.close?.();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();

    this.notifications.setEnabled(false);
    this.notifications.disconnectSse();
    this.notifications.stopPolling();
  }
}
