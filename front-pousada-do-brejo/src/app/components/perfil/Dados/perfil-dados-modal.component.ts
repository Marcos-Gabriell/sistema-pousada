// src/app/components/perfil/Dados/perfil-dados-modal.component.ts
import {
  Component, EventEmitter, HostListener, Input, Output, OnChanges, SimpleChanges, OnDestroy,
  inject, signal, computed, ViewChild, ElementRef, ChangeDetectorRef, NgZone
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { ImageCropperComponent, ImageCroppedEvent } from 'ngx-image-cropper';

import { ToastService } from '../../../toast/toast.service';
import { AuthService } from '../../../services/login/auth.service';
import { PerfilService, CurrentUser, UpdateSelfDto, AvatarView } from '../perfil.service';
import { ModalScrollService } from '../../../services/modal-scroll.service';

@Component({
  standalone: true,
  selector: 'app-perfil-dados-modal',
  imports: [CommonModule, ReactiveFormsModule, ImageCropperComponent],
  templateUrl: './perfil-dados-modal.component.html',
})
export class PerfilDadosModalComponent implements OnChanges {
  @Input() open = false;
  @Output() close = new EventEmitter<void>();

  @ViewChild('fileChooser') fileChooser!: ElementRef<HTMLInputElement>;

  private auth   = inject(AuthService);
  private perfil = inject(PerfilService);
  private fb     = inject(FormBuilder);
  private toast  = inject(ToastService);
  private cdr    = inject(ChangeDetectorRef);
  private zone   = inject(NgZone);
  private modalScroll = inject(ModalScrollService);

  carregando     = signal(false);
  salvando       = signal(false);
  salvandoAvatar = signal(false);
  me             = signal<CurrentUser | null>(null);

  palette = ['#7F00FF','#00C2FF','#0EA5E9','#22C55E','#F59E0B','#EF4444','#64748B','#111827'];

  private _avatarMenuOpen = signal(false);
  avatarMenuOpen = this._avatarMenuOpen.asReadonly();

  avatarMode = computed(() => this.me()?.avatar?.mode || 'COLOR');

  iniciais = computed(() => {
    const n = this.me()?.nome?.trim() || 'U';
    return n
      .split(/\s+/)
      .filter(Boolean)
      .map((p: string) => p[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  });

  // ====== Cropper ======
  cropOpen = false;
  imageChangedEvent: any = null;
  croppedImage: string | null = null;

  // ====== Form ======
  private phone11Optional = (c: AbstractControl): ValidationErrors | null => {
    const d = this.onlyDigits(c.value);
    if (!d) return null;
    return d.length === 11 ? null : { phone: true };
  };

  form = this.fb.group({
    nome: ['', [Validators.required, Validators.maxLength(15), Validators.pattern(/^[A-Za-zÀ-ÖØ-öø-ÿ ]+$/)]],
    email: ['', [Validators.required, Validators.email]],
    numero: ['', [this.phone11Optional]],
    username: ['']
  });

  ngOnChanges(ch: SimpleChanges) {
    if (ch['open']?.currentValue === true) this.onOpen();
  }

  private onOpen() {
    const snap = this.auth.getCurrentUser() as any;
    this.me.set(snap || null);

    this.form.reset({
      nome: snap?.nome || '',
      email: snap?.email || '',
      numero: this.formatPhone(this.onlyDigits(snap?.numero || '')),
      username: snap?.username || ''
    });

    this.carregando.set(true);

    this.perfil.getMe().subscribe({
      next: (fresh) => {
        const merged: CurrentUser = { ...(this.me() || {} as any), ...(fresh as any) };
        this.me.set(merged);
        this.auth.setCurrentUser(merged as any);

        this.form.patchValue({
          nome: merged?.nome || '',
          email: merged?.email || '',
          numero: this.formatPhone(this.onlyDigits(merged?.numero || '')),
          username: merged?.username || ''
        });

        this.refreshAvatar(true);
      },
      error: () => {
        this.carregando.set(false);
      }
    });

    this.modalScroll.lock();
  }

  private refreshAvatar(finishLoading = false) {
    this.perfil.getMeuAvatar().subscribe({
      next: (avatar: AvatarView) => {
        const prev = this.me();
        if (prev) {
          const merged: CurrentUser = { ...prev, avatar };
          this.me.set(merged);
          this.auth.setCurrentUser(merged as any);
        }
        if (finishLoading) this.carregando.set(false);
      },
      error: () => {
        if (finishLoading) this.carregando.set(false);
      }
    });
  }

  fechar() {
    this.modalScroll.unlock();
    this._avatarMenuOpen.set(false);
    this.close.emit();
  }

  ngOnDestroy(): void {
    try { if (this.open) this.modalScroll.unlock(); } catch { /* noop */ }
  }

  @HostListener('document:keydown', ['$event'])
  onKey(ev: KeyboardEvent) {
    if (!this.open) return;
    if (ev.key === 'Escape') this.fechar();
  }

  toggleAvatarMenu(ev?: Event) {
    ev?.stopPropagation();
    this._avatarMenuOpen.update(v => !v);
    if (this._avatarMenuOpen()) {
      setTimeout(() => {
        const close = (e: any) => {
          if (!(e?.target as HTMLElement)?.closest('[role="menu"]')) this.closeAvatarMenu();
          document.removeEventListener('click', close, { capture: true } as any);
        };
        document.addEventListener('click', close, { capture: true });
      });
    }
  }

  closeAvatarMenu() {
    this._avatarMenuOpen.set(false);
  }

  triggerFile() {
    if (this.salvandoAvatar()) return;
    this.fileChooser?.nativeElement?.click();
  }

  startCrop(ev: Event) {
    if (this.salvandoAvatar()) return;

    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    if (!/^image\/(png|jpe?g)$/i.test(file.type)) {
      this.toast.warning('Envie PNG ou JPG.');
      input.value = '';
      return;
    }
    if (file.size > 3_000_000) {
      this.toast.warning('Máximo 3MB.');
      input.value = '';
      return;
    }

    this.croppedImage = null;
    this.imageChangedEvent = null;

    this.cropOpen = true;
    this.closeAvatarMenu();

    setTimeout(() => {
      this.imageChangedEvent = ev;
      this.cdr.detectChanges();
    }, 300);
  }

  onCropped(e: ImageCroppedEvent) {
    if (e.base64) {
      this.zone.run(() => {
        this.croppedImage = e.base64 ?? null;
        this.cdr.detectChanges();
      });
    } else if (e.blob) {
      this.convertBlobToBase64(e.blob);
    } else {
      this.toast.warning('Erro ao processar imagem. Tente novamente.');
    }
  }

  private convertBlobToBase64(blob: Blob) {
    const reader = new FileReader();
    reader.onload = () => {
      this.zone.run(() => {
        this.croppedImage = reader.result as string;
        this.cdr.detectChanges();
      });
    };
    reader.onerror = (error) => {
      console.error('Erro ao converter blob:', error);
      this.toast.error('Erro ao processar imagem.');
    };
    reader.readAsDataURL(blob);
  }

  cancelCrop() {
    this.cropOpen = false;
    this.imageChangedEvent = null;
    this.croppedImage = null;
    if (this.fileChooser?.nativeElement) this.fileChooser.nativeElement.value = '';
  }

  confirmCrop() {
    if (!this.croppedImage) {
      this.toast.warning('Ajuste a imagem antes de aplicar.');
      return;
    }

    this.salvandoAvatar.set(true);

    this.perfil.enviarAvatarBase64(this.croppedImage).subscribe({
      next: () => {
        this.toast.success('Avatar atualizado com sucesso!');
        this.refreshAvatar();
        this.cancelCrop();
      },
      error: (err: HttpErrorResponse) => {
        console.error('Erro ao enviar avatar:', err);
        this.toast.error(err?.error?.error || 'Falha ao enviar avatar.');
      },
      complete: () => {
        this.salvandoAvatar.set(false);
      }
    });
  }

  // ====== Cor ======
  setColor(hex: string) {
    this.perfil.setAvatarColor(hex).subscribe({
      next: () => {
        this.toast.success('Cor aplicada.');
        this.refreshAvatar();
        this.closeAvatarMenu();
      },
      error: (err: HttpErrorResponse) =>
        this.toast.error(err?.error?.error || 'Falha ao aplicar cor.')
    });
  }

  removerFoto() {
    const defaultColor = '#64748B';
    this.perfil.setAvatarColor(defaultColor).subscribe({
      next: () => {
        this.toast.success('Foto removida. Cor aplicada.');
        this.refreshAvatar();
        this.closeAvatarMenu();
      },
      error: (err: HttpErrorResponse) => {
        this.toast.error(err?.error?.error || 'Falha ao remover foto.');
      }
    });
  }

  // ====== Helpers ======
  private onlyDigits(v: any): string {
    return (v ?? '').toString().replace(/\D/g, '');
  }

  private formatPhone(d: string): string {
    const s = (d || '').slice(0, 11);
    if (!s) return '';
    const dd = s.slice(0, 2);
    if (s.length <= 2) return `(${dd}`;
    if (s.length <= 7) return `(${dd}) ${s.slice(2, 7)}`;
    return `(${dd}) ${s.slice(2, 7)}-${s.slice(7, 11)}`;
  }

  onNumeroInput(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const digits = this.onlyDigits(input.value);
    const masked = this.formatPhone(digits);
    this.form.get('numero')?.setValue(masked, { emitEvent: false });
  }

  onNumeroPaste(ev: ClipboardEvent) {
    ev.preventDefault();
    const text = ev.clipboardData?.getData('text') || '';
    const digits = this.onlyDigits(text);
    const masked = this.formatPhone(digits);
    this.form.get('numero')?.setValue(masked);
  }

  private alterado(): boolean {
    const u = this.me();
    if (!u) return true;
    const f = this.form.value as any;
    const dOrig = this.onlyDigits(u.numero || '');
    const dForm = this.onlyDigits(f.numero || '');
    return (
      (u.nome || '') !== (f.nome || '') ||
      (u.email || '') !== (f.email || '') ||
      (u.username || '') !== (f.username || '') ||
      dOrig !== dForm
    );
  }

  salvar() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.warning('Corrija os campos destacados antes de salvar.');
      return;
    }
    if (!this.alterado()) {
      this.toast.info('Nenhuma alteração a salvar.');
      return;
    }

    const digits = this.onlyDigits(this.form.value.numero || '');
    const dto: UpdateSelfDto = {
      nome: (this.form.value.nome || '').trim(),
      email: (this.form.value.email || '').trim(),
      numero: digits ? digits : null,
      username: ((this.form.value.username || '').trim() || null) as any
    };

    this.salvando.set(true);
    this.perfil.atualizarMe(dto).subscribe({
      next: (updated) => {
        const prev = this.auth.getCurrentUser() as any;
        const merged: CurrentUser = {
          id:        (updated as any)?.id ?? prev?.id,
          roles:     (updated as any)?.roles ?? prev?.roles,
          nome:      (updated as any)?.nome ?? prev?.nome,
          email:     (updated as any)?.email ?? prev?.email,
          numero:    (updated as any)?.numero ?? prev?.numero,
          username:  (updated as any)?.username ?? prev?.username,
          avatar:    (updated as any)?.avatar ?? prev?.avatar,
          avatarUrl: (updated as any)?.avatarUrl ?? prev?.avatarUrl
        };
        this.auth.setCurrentUser(merged as any);
        this.me.set(merged);
        this.toast.success('Dados atualizados com sucesso.');
        this.salvando.set(false);
        this.fechar();
      },
      error: (e: HttpErrorResponse) => {
        const msg = (e?.error && (e.error.error || e.error.mensagem)) || 'Falha ao atualizar dados.';
        this.toast.error(msg);
        this.salvando.set(false);
      }
    });
  }
}
