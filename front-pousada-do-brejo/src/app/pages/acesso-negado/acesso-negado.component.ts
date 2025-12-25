import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/login/auth.service';

@Component({
  selector: 'app-acesso-negado',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="flex items-center justify-center min-h-screen bg-[var(--bg-color)] p-4">
      <section
        class="flex flex-col items-center w-full max-w-md py-8 px-10
               bg-[var(--bg-card)] rounded-2xl border border-[var(--border-color)]
               shadow-md text-center text-[var(--text-color)]"
      >
        <div class="text-5xl leading-none mb-5 text-[var(--text-color)]">🔒</div>

        <h1 class="text-2xl font-semibold mb-3">
          Acesso negado
        </h1>

        <p class="text-base text-[var(--text-dim)] leading-relaxed mb-6">
          {{ mensagem() }}
        </p>

        <div class="flex flex-col sm:flex-row sm:justify-center gap-3 w-full">
          <a *ngIf="!isLogado()" routerLink="/login"
             class="block sm:w-auto py-3 px-5 rounded-lg font-medium text-base
                    bg-[var(--text-color)] text-[var(--bg-color)]
                    hover:opacity-90 transition">
            Fazer login
          </a>
          <a *ngIf="isLogado()" routerLink="/dashboard"
             class="block sm:w-auto py-3 px-5 rounded-lg font-medium text-base
                    bg-[var(--text-color)] text-[var(--bg-color)]
                    hover:opacity-90 transition">
            Ir para o dashboard
          </a>
          <a routerLink="/"
             class="block sm:w-auto py-3 px-5 rounded-lg font-medium text-base
                    bg-[var(--bg-card)] text-[var(--text-color)]
                    border border-[var(--border-color)]
                    hover:bg-[var(--table-row-hover)] transition">
            Página inicial
          </a>
        </div>

        <small class="text-[var(--text-dim)] mt-6 text-sm">
          {{ hint() }}
        </small>
      </section>
    </div>
  `,
})
export class AcessoNegadoComponent {
  private auth = inject(AuthService);

  isLogado(): boolean {
    const token = this.auth.getToken?.() || '';
    return this.auth.isTokenValid ? !!this.auth.isTokenValid() : !!token;
  }

  mensagem(): string {
    if (!this.isLogado()) {
      return 'Você precisa estar autenticado para acessar esta página.';
    }
    return 'Você não tem permissão para acessar esta página.';
  }

  hint(): string {
    if (!this.isLogado()) {
      return 'Faça login para continuar.';
    }
    return 'Se achar que isso é um engano, fale com um administrador.';
  }
}
