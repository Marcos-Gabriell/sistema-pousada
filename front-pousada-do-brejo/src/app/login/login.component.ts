import { Component, OnInit, HostListener } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../services/login/auth.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ToastService } from '../toast/toast.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
})
export class LoginComponent implements OnInit {
  usuario = '';
  senha = '';
  isLoading = false;

  // modal "Esqueceu a senha?"
  showRecuperar = false;

  // controle de exibição da senha
  mostrarSenha = false;

  logoSrc = 'assets/logo.png';

  constructor(
    private auth: AuthService,
    private router: Router,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.atualizarLogoPeloTema();
  }

  // se o tema mudar dinamicamente (classe trocada no html/body)
  @HostListener('document:click')
  onThemeChange() {
    this.atualizarLogoPeloTema();
  }

  private atualizarLogoPeloTema() {
    const isDark =
      document.documentElement.classList.contains('dark-theme') ||
      document.body.classList.contains('dark-theme');
    this.logoSrc = isDark ? 'assets/logo-branca.png' : 'assets/logo.png';
  }

  onSubmit(form: any) {
    if (!form.valid) return;

    this.usuario = this.usuario.trim();
    this.senha = this.senha.trim();

    if (this.usuario.length > 50) {
      this.toast.warning('Identificador deve ter no máximo 50 caracteres.');
      return;
    }
    if (this.senha.length > 50) {
      this.toast.warning('Senha deve ter no máximo 50 caracteres.');
      return;
    }

    this.isLoading = true;
    this.auth.login(this.usuario, this.senha).subscribe({
      next: () => {
        this.isLoading = false;
        this.toast.success('Login bem-sucedido! Redirecionando...');
        this.router.navigate(['/dashboard']);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        const msg =
          err?.error?.error || err?.error?.mensagem || err?.error?.message;

        if (err.status === 401 || err.status === 403) {
          if (
            typeof msg === 'string' &&
            msg.toLowerCase().includes('usuário ou senha inválidos')
          ) {
            this.toast.error('🔑 Usuário ou senha inválidos. Tente novamente.');
          } else if (
            typeof msg === 'string' &&
            (msg.toLowerCase().includes('muitas tentativas') ||
              msg.toLowerCase().includes('conta está inativa'))
          ) {
            this.toast.warning(msg);
          } else {
            this.toast.error(
              '❌ Erro de autenticação. Verifique suas credenciais.'
            );
          }
        } else if (err.status === 0) {
          this.toast.error('🌐 Sem conexão com o servidor. Verifique sua rede.');
        } else {
          this.toast.error(
            'Ocorreu um erro inesperado. Tente novamente mais tarde.'
          );
        }
      },
    });
  }

  // controle do ícone de ver/ocultar senha
  toggleSenha() {
    this.mostrarSenha = !this.mostrarSenha;
  }

  // abrir/fechar modal de recuperação
  abrirRecuperar() {
    this.showRecuperar = true;
    setTimeout(() => {
      (document.querySelector('#recuperar-dialog') as HTMLElement)?.focus();
    });
  }

  fecharRecuperar() {
    this.showRecuperar = false;
  }

  onBackdropClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (target?.id === 'recuperar-backdrop') {
      this.fecharRecuperar();
    }
  }
}
