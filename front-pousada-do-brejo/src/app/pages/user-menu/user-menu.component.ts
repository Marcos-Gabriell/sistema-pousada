import {
  Component,
  HostListener,
  inject,
  signal,
  computed,
  effect,
  OnInit,
  Output,
  EventEmitter,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/login/auth.service';
import { NotificationsService } from '../../services/notifications/notifications.service';
import { PerfilService, AvatarView } from '../../components/perfil/perfil.service';

type Mode = 'PHOTO' | 'COLOR';

@Component({
  standalone: true,
  selector: 'app-user-menu',
  imports: [CommonModule, RouterModule],
  templateUrl: './user-menu.component.html',
})
export class UserMenuComponent implements OnInit {
  aberto = signal(false);
  confirmOpen = signal(false);

  @Output() openProfile = new EventEmitter<void>();
  @Output() openPassword = new EventEmitter<void>();
  @Output() openNotifications = new EventEmitter<void>();

  private auth = inject(AuthService);
  private noti = inject(NotificationsService);
  private perfil = inject(PerfilService);

  me = this.auth.user;

  cachedMode = signal<Mode>('COLOR');
  cachedColor = signal<string | undefined>(undefined);
  cachedUrl = signal<string | undefined>(undefined);
  photoLoading = signal<boolean>(false);

  unread = computed(() => this.noti.unread());

  ngOnInit(): void {
    this.loadMeuAvatar();
  }

  private loadMeuAvatar() {
    this.perfil.getMeuAvatar().subscribe({
      next: (avatar: AvatarView) => {
        // merge com usuário atual e atualiza globalmente
        const cur = (this.auth.getCurrentUser && this.auth.getCurrentUser()) || {} as any;
        this.auth.setCurrentUser?.({ ...cur, avatar } as any);
      },
      error: () => {
        // Estado padrão seguro
        const currentVersion = ((this.auth.getCurrentUser && (this.auth.getCurrentUser() as any)?.avatar?.version) ?? 0) + 1;
        const cur = (this.auth.getCurrentUser && this.auth.getCurrentUser()) || {} as any;
        this.auth.setCurrentUser?.({
          ...cur,
          avatar: {
            mode: 'COLOR',
            color: '#64748B',
            gradient: null,
            dataUrl: null,
            version: currentVersion,
          } as AvatarView,
        } as any);
      },
    });
  }

  public refreshAvatar(): void {
    this.loadMeuAvatar();
  }

  iniciais(): string {
    const nome = this.me()?.nome || '';
    if (!nome) return 'U';
    const p = nome.trim().split(/\s+/).filter(Boolean);
    if (p.length === 1 && p[0].length > 1) return p[0].slice(0, 2).toUpperCase();
    if (p.length > 1) return (p[0][0] + p[p.length - 1][0]).toUpperCase();
    return (p[0]?.[0] || 'U').toUpperCase();
  }

  private sync = effect(() => {
    const avatar = (this.me() as any)?.avatar;
    const mode = (avatar?.mode as Mode) || 'COLOR';

    if (mode === 'PHOTO' && avatar?.dataUrl) {
      if (this.cachedUrl() === avatar.dataUrl && this.cachedMode() === 'PHOTO') return;

      this.photoLoading.set(true);
      const img = new Image();
      img.onload = () => {
        this.cachedUrl.set(avatar.dataUrl!);
        this.cachedMode.set('PHOTO');
        this.cachedColor.set(undefined);
        this.photoLoading.set(false);
      };
      img.onerror = () => {
        this.cachedMode.set('COLOR');
        this.cachedUrl.set(undefined);
        this.cachedColor.set('#64748B');
        this.photoLoading.set(false);
      };
      img.src = avatar.dataUrl;
      return;
    }

    // COLOR mode
    this.cachedMode.set('COLOR');
    this.cachedColor.set(avatar?.color || '#64748B');
    this.cachedUrl.set(undefined);
    this.photoLoading.set(false);
  });

  onPhotoLoad() {
    this.photoLoading.set(false);
  }
  onPhotoError() {
    this.photoLoading.set(false);
    this.cachedMode.set('COLOR');
    this.cachedUrl.set(undefined);
  }

  // ===== MENU CONTROLES =====
  toggle() {
    this.aberto.update((v) => !v);
  }
  fechar() {
    this.aberto.set(false);
  }

  abrirPerfil() {
    this.openProfile.emit();
    this.fechar();
  }
  abrirSenha() {
    this.openPassword.emit();
    this.fechar();
  }
  abrirNotificacoes() {
    this.openNotifications.emit();
    this.fechar();
  }

  abrirConfirmarLogout() {
    this.fechar();
    this.confirmOpen.set(true);
    document.documentElement.classList.add('overflow-hidden');
  }
  cancelarLogout() {
    this.confirmOpen.set(false);
    document.documentElement.classList.remove('overflow-hidden');
  }
  confirmarLogout() {
    try {
      this.auth.logout();
    } finally {
      this.cancelarLogout();
    }
  }

  // ===== LISTENERS =====
  @HostListener('document:click', ['$event'])
  onDocClick(ev: MouseEvent) {
    const el = ev.target as HTMLElement;
    if (!el.closest('[data-umenu-root]')) this.fechar();
  }

  @HostListener('window:keydown', ['$event'])
  onKey(e: KeyboardEvent) {
    if (this.confirmOpen()) {
      if (e.key === 'Escape') {
        e.preventDefault();
        this.cancelarLogout();
      }
      if (e.key === 'Enter') {
        e.preventDefault();
        this.confirmarLogout();
      }
    }
  }
}
