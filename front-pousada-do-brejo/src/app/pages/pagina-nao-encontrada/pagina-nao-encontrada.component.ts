// src/app/pages/pagina-nao-encontrada/pagina-nao-encontrada.component.ts
import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-pagina-nao-encontrada',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid min-h-dvh place-items-center bg-[var(--bg-color)] p-6">
      <div class="w-full max-w-lg text-center text-[var(--text-color)]">
        <div class="mx-auto mb-4 h-[104px] w-[104px] grid place-items-center rounded-full
                    border border-[var(--border-color)] bg-[var(--bg-card)]">
          <span class="text-4xl text-[var(--text-color)]">🧐</span>
        </div>

        <h1 class="mb-1.5 mt-2 text-2xl font-bold">
          404 — Página não encontrada
        </h1>
        <p class="mb-4 text-[var(--text-dim)]">
          A rota que você tentou acessar não existe ou foi movida.
        </p>

        <div class="my-4 flex flex-wrap justify-center gap-3">
          <a
            routerLink="/"
            class="inline-flex items-center justify-center rounded-lg px-4 py-2.5 font-semibold
                   bg-[var(--accent-yellow)] text-black shadow-sm transition
                   hover:brightness-110"
          >
            Voltar ao início
          </a>
          <a
            routerLink="/dashboard"
            class="inline-flex items-center justify-center rounded-lg border border-[var(--border-color)]
                   px-4 py-2.5 font-semibold bg-[var(--bg-card)]
                   text-[var(--text-color)] shadow-sm transition
                   hover:bg-[var(--table-row-hover)]"
          >
            Ir para o dashboard
          </a>
        </div>

        <small class="mt-2 block text-sm text-[var(--text-dim)]">
          Código: <strong class="font-semibold text-[var(--text-color)]">404</strong> •
          Se o problema persistir, fale com o administrador.
        </small>
      </div>
    </div>
  `,
})
export class PaginaNaoEncontradaComponent {}
