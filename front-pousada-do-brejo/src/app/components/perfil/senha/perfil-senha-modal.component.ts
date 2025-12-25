import {
  Component,
  EventEmitter,
  HostListener,
  Input,
  Output,
  inject,
  signal,
  OnInit,
  OnChanges,
  OnDestroy,
  SimpleChanges
} from '@angular/core';
import {
  ReactiveFormsModule,
  FormBuilder,
  Validators,
  AbstractControl,
  ValidationErrors,
  FormGroup
} from '@angular/forms';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';

import { PerfilService } from '../perfil.service';
import { ToastService } from '../../../toast/toast.service';
import { ModalScrollService } from '../../../services/modal-scroll.service';

@Component({
  standalone: true,
  selector: 'app-perfil-senha-modal',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './perfil-senha-modal.component.html'
})
export class PerfilSenhaModalComponent implements OnInit, OnChanges, OnDestroy {
  @Input() open = false;
  @Output() close = new EventEmitter<void>();
  @Input() trocaObrigatoria = false;

  private perfil: PerfilService = inject(PerfilService);
  private modalScroll = inject(ModalScrollService);
  private fb: FormBuilder = inject(FormBuilder);
  private toast: ToastService = inject(ToastService);

  salvando = signal(false);

  passwordFields: { [key: string]: { visible: boolean } } = {
    senhaAtual: { visible: false },
    novaSenha: { visible: false },
    confirmarSenha: { visible: false }
  };

  form = this.fb.group(
    {
      senhaAtual: ['', [Validators.required]],
      novaSenha: [
        '',
        [
          Validators.required,
          Validators.minLength(5),
          PerfilSenhaModalComponent.temDigito
        ]
      ],
      confirmarSenha: ['', [Validators.required]]
    },
    { validators: PerfilSenhaModalComponent.senhasIguais }
  );

  // ---------- LIFECYCLE ----------
  ngOnInit() {
    this.atualizarValidadores();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['trocaObrigatoria']) {
      this.atualizarValidadores();
    }

    if (changes['open']?.currentValue === true && !changes['open']?.previousValue) {
      this.modalScroll.lock();
    }
    if (changes['open']?.currentValue === false && changes['open']?.previousValue) {
      this.modalScroll.unlock();
    }
  }

  ngOnDestroy(): void {
    if (this.open) this.modalScroll.unlock();
  }

  // ---------- VALIDADORES ESTÁTICOS ----------
  static temDigito(ctrl: AbstractControl): ValidationErrors | null {
    const v = String(ctrl.value ?? '');
    return /\d/.test(v) ? null : { digito: true };
  }

  static senhasIguais(group: AbstractControl): ValidationErrors | null {
    const formGroup = group as FormGroup;
    const nova = formGroup.get('novaSenha')?.value;
    const conf = formGroup.get('confirmarSenha')?.value;
    return nova === conf ? null : { mismatch: true };
  }

  // ---------- REGRAS DINÂMICAS ----------
  private atualizarValidadores() {
    const senhaAtualControl = this.form.get('senhaAtual');
    if (!senhaAtualControl) return;

    if (this.trocaObrigatoria) {
      // Remove a validação de obrigatoriedade da senha atual
      senhaAtualControl.clearValidators();
      senhaAtualControl.updateValueAndValidity();
    } else {
      // Adiciona a validação de obrigatoriedade
      senhaAtualControl.setValidators([Validators.required]);
      senhaAtualControl.updateValueAndValidity();
    }
  }

  // ---------- VISIBILIDADE CAMPOS ----------
  togglePasswordVisibility(fieldName: string) {
    if (this.passwordFields[fieldName]) {
      this.passwordFields[fieldName].visible =
        !this.passwordFields[fieldName].visible;
    }
  }

  // ---------- FORÇA DA SENHA ----------
  getPasswordStrength(): string {
    const password = this.form.get('novaSenha')?.value || '';
    if (password.length < 5) return 'fraca';
    if (password.length < 8 || !this.hasDigit() || !this.hasSpecialChar()) {
      return 'media';
    }
    return 'forte';
  }

  getPasswordStrengthText(): string {
    const strength = this.getPasswordStrength();
    switch (strength) {
      case 'fraca':
        return 'Fraca';
      case 'media':
        return 'Média';
      case 'forte':
        return 'Forte';
      default:
        return '';
    }
  }

  hasDigit(): boolean {
    const password = this.form.get('novaSenha')?.value || '';
    return /\d/.test(password);
  }

  hasSpecialChar(): boolean {
    const password = this.form.get('novaSenha')?.value || '';
    return /[!@#$%&*]/.test(password);
  }

  // ---------- CONTROLES DO MODAL ----------
  fechar() {
    // Não deixa fechar enquanto estiver salvando
    if (this.salvando()) return;

    this.close.emit();
    this.resetForm();
  }

  resetForm() {
    this.form.reset();
    Object.keys(this.passwordFields).forEach((key) => {
      this.passwordFields[key].visible = false;
    });
  }

  @HostListener('document:keydown', ['$event'])
  onKey(ev: KeyboardEvent) {
    if (!this.open) return;
    if (ev.key === 'Escape') {
      this.fechar();
    }
  }

  // ---------- SALVAR ----------
  salvar() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { senhaAtual, novaSenha, confirmarSenha } = this.form.value as any;

    // Se for troca obrigatória, não envia senhaAtual
    const dados = this.trocaObrigatoria
      ? { novaSenha, confirmarSenha }
      : { senhaAtual, novaSenha, confirmarSenha };

    this.salvando.set(true);

    this.perfil.trocarSenha(dados).subscribe({
      next: () => {
        this.toast.success('Senha alterada com sucesso!');
        this.salvando.set(false);
        this.resetForm();
        this.fechar();
      },
      error: (e: HttpErrorResponse) => {
        const msg =
          (e?.error && (e.error.error || e.error.mensagem)) ||
          'Falha ao alterar senha.';
        this.toast.error(msg);
        this.salvando.set(false);
      }
    });
  }
}
