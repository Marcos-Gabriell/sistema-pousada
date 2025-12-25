// src/app/components/account/force-password-change-modal.component.ts
import {
  Component,
  HostListener,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PasswordPromptService } from '../../services/login/password-prompt.service';
import { UsersService } from '../../pages/users/users.service';
import { ToastService } from '../../toast/toast.service';
import { AuthService } from '../../services/login/auth.service';
import { Subscription } from 'rxjs';
import { ModalScrollService } from '../../services/modal-scroll.service';

@Component({
  selector: 'app-force-password-change-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  styles: [`
    /* animações */
    @keyframes check-draw { to { stroke-dashoffset: 0; } }
    @keyframes pop { 0% { transform: scale(.9); opacity: 0; } 100% { transform: scale(1); opacity: 1; } }
    @keyframes shrink-out { to { transform: scale(.92); opacity: 0; filter: blur(1px); } }
    @keyframes outlinePulse {
      0% { box-shadow: 0 0 0 0 rgba(34,197,94,.45); }
      100% { box-shadow: 0 0 0 14px rgba(34,197,94,0); }
    }
    .check-anim { stroke-dasharray: 48; stroke-dashoffset: 48; animation: check-draw .6s ease-out .15s forwards; }
    .pop-in { animation: pop .22s ease-out both; }
    .closing .success-card { animation: shrink-out .22s ease-in both; }

    /* tema claro: suaviza os blocos */
    .light-card {
      background: #fff;
      border: 1px solid rgba(15, 23, 42, 0.08);
      box-shadow: 0 12px 36px rgba(15, 23, 42, 0.12);
    }
    .light-border {
      border-color: rgba(15, 23, 42, 0.05) !important;
    }
  `],
  template: `
  <!-- OVERLAY PRINCIPAL -->
  <div *ngIf="!showSuccess"
       [ngClass]="isDarkTheme ? 'bg-black/45' : 'bg-black/25'"
       class="fixed inset-0 z-[1000] flex items-center justify-center backdrop-blur-sm p-4"
       role="dialog" aria-modal="true" aria-label="Alteração de senha obrigatória">

    <!-- MODAL -->
    <div
      [ngClass]="isDarkTheme
        ? 'bg-[var(--bg-card)] border-[var(--border-color)]'
        : 'light-card'"
      class="w-[min(94vw,760px)] rounded-3xl border
             backdrop-blur-md
             shadow-2xl ring-1 ring-black/5 overflow-hidden"
    >

      <!-- HEADER -->
      <div
        class="relative px-6 sm:px-8 pt-6 pb-4 border-b"
        [ngClass]="isDarkTheme ? 'border-[var(--border-color)] bg-[var(--bg-card)]' : 'light-border bg-white'"
        *ngIf="(reason$ | async) as reason">

        <!-- barra de progresso no topo -->
        <div *ngIf="(step$ | async) as step">
          <ng-container *ngIf="(prompt.stepMax$ | async) as max">
            <div
              class="absolute inset-x-0 top-0 h-1.5 overflow-hidden"
              [ngClass]="isDarkTheme ? 'bg-[var(--bg-color)]/60' : 'bg-slate-100'">
              <div class="h-full transition-all duration-300"
                   [ngClass]="isDarkTheme ? 'bg-[var(--text-color)]/90' : 'bg-amber-400'"
                   [style.width.%]="(step/max)*100"></div>
            </div>
          </ng-container>
        </div>

        <!-- badge (Passo 1 de 3) -->
        <div class="absolute top-4 right-6 z-[60]" *ngIf="(step$ | async) as step2">
          <ng-container *ngIf="(prompt.stepMax$ | async) as max2">
            <div
              class="inline-flex items-center gap-2 rounded-full px-2.5 py-1.5 border shadow-sm transition"
              [ngClass]="isDarkTheme
                ? 'bg-[var(--bg-card)]/95 border-[var(--border-color)]'
                : 'bg-white/95 border-slate-200'"
            >
              <span class="sr-only">Progresso: passo {{ step2 }} de {{ max2 }}</span>
              <span [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-700'" class="text-[11px] font-semibold">
                Passo {{ step2 }} de {{ max2 }}
              </span>
            </div>
          </ng-container>
        </div>

        <!-- título -->
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2
              [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'"
              class="text-2xl sm:text-[28px] font-extrabold tracking-tight flex items-center gap-2">
              <ng-container *ngIf="reason === 'RESET_BY_ADMIN'; else first">
                Senha redefinida
              </ng-container>
              <ng-template #first>Bem-vindo, {{ firstName || 'usuário' }}</ng-template>
            </h2>
            <p
              [ngClass]="isDarkTheme ? 'text-[var(--text-dim)]' : 'text-slate-500'"
              class="text-sm sm:text-[15px] mt-1.5">
              <ng-container *ngIf="reason === 'RESET_BY_ADMIN'; else subFirst">
                Sua senha foi resetada pelo administrador. Por segurança, defina uma nova senha antes de continuar.
              </ng-container>
              <ng-template #subFirst>
                Vamos te mostrar rapidinho como o sistema funciona. Depois, você cria sua senha.
              </ng-template>
            </p>
          </div>
        </div>
      </div>

      <!-- BODY -->
      <div class="px-6 sm:px-8 py-6" *ngIf="(step$ | async) as step; else loading">
        <ng-container *ngIf="(reason$ | async) as reason">

          <!-- STEP 1 -->
          <section *ngIf="step === 1" class="space-y-5">
            <ng-container *ngIf="reason === 'RESET_BY_ADMIN'; else firstLoginIntro">
              <h3
                [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'"
                class="text-lg sm:text-xl font-bold flex items-center gap-2">
                Sua senha foi resetada
              </h3>
              <ul
                [ngClass]="isDarkTheme ? 'text-[var(--text-color)]/90' : 'text-slate-700'"
                class="space-y-2 text-[15px]">
                <li class="flex gap-2"><span>🛡️</span><span>Medida preventiva: redefina a senha para recuperar acesso total.</span></li>
                <li class="flex gap-2"><span>🔑</span><span>Prefira algo só seu, misturando letras e números.</span></li>
                <li class="flex gap-2"><span>❗</span><span>Evite reutilizar senhas e não compartilhe com ninguém.</span></li>
              </ul>
              <div
                [ngClass]="isDarkTheme
                  ? 'border-[var(--border-color)] bg-[var(--bg-color)]/70 text-[var(--text-dim)]'
                  : 'border-slate-200 bg-slate-50 text-slate-500'"
                class="rounded-xl border p-3 text-[13.5px]">
                Dica: padrões como
                <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">123456</b>
                ou
                <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">senha</b>
                são frágeis.
              </div>
            </ng-container>

            <ng-template #firstLoginIntro>
              <h3
                [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'"
                class="text-lg sm:text-xl font-bold">
                O que você vai usar no dia a dia
              </h3>
              <ul class="grid sm:grid-cols-2 gap-3 text-[15px]"
                  [ngClass]="isDarkTheme ? 'text-[var(--text-dim)]' : 'text-slate-500'">
                <li class="flex gap-3 items-start">
                  <div
                    [ngClass]="isDarkTheme ? 'bg-[var(--table-row-hover)]' : 'bg-slate-100'"
                    class="mt-0.5 shrink-0 rounded-lg px-2 py-1 text-[var(--text-color)]">🏨</div>
                  <div>
                    <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">Hospedagens</b> — cadastre e gerencie estadias.
                  </div>
                </li>
                <li class="flex gap-3 items-start">
                  <div
                    [ngClass]="isDarkTheme ? 'bg-[var(--table-row-hover)]' : 'bg-slate-100'"
                    class="mt-0.5 shrink-0 rounded-lg px-2 py-1 text-[var(--text-color)]">🛏️</div>
                  <div>
                    <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">Quartos</b> — gerencie quartos e status.
                  </div>
                </li>
                <li class="flex gap-3 items-start">
                  <div
                    [ngClass]="isDarkTheme ? 'bg-[var(--table-row-hover)]' : 'bg-slate-100'"
                    class="mt-0.5 shrink-0 rounded-lg px-2 py-1 text-[var(--text-color)]">↩️</div>
                  <div>
                    <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">Saídas</b> — registre saídas de caixa.
                  </div>
                </li>
                <li class="flex gap-3 items-start">
                  <div
                    [ngClass]="isDarkTheme ? 'bg-[var(--table-row-hover)]' : 'bg-slate-100'"
                    class="mt-0.5 shrink-0 rounded-lg px-2 py-1 text-[var(--text-color)]">📅</div>
                  <div>
                    <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">Reservas</b> — crie e acompanhe reservas.
                  </div>
                </li>
              </ul>
              <div
                [ngClass]="isDarkTheme
                  ? 'border-[var(--border-color)] bg-[var(--bg-color)]/70 text-[var(--text-dim)]'
                  : 'border-slate-200 bg-slate-50 text-slate-500'"
                class="rounded-xl border p-3 text-[13.5px]">
                Dica: ações mais comuns ficam em
                <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">Hospedagens</b>
                e
                <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">Saídas</b>.
              </div>
            </ng-template>
          </section>

          <!-- STEP 2 -->
          <section *ngIf="reason !== 'RESET_BY_ADMIN' && step === 2" class="space-y-4">
            <h3 [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'"
                class="text-lg sm:text-xl font-bold">Dicas rápidas & suporte</h3>
            <ul [ngClass]="isDarkTheme ? 'text-[var(--text-dim)]' : 'text-slate-600'"
                class="space-y-2 text-[15px]">
              <li>📝 Mantenha seus dados atualizados.</li>
              <li>🔒 Não compartilhe sua senha.</li>
              <li>🆘 Se esquecer, peça ao administrador para resetar.</li>
            </ul>
          </section>

          <!-- STEP SENHA -->
          <section *ngIf="(reason === 'RESET_BY_ADMIN' && step === 2) || (reason !== 'RESET_BY_ADMIN' && step === 3)"
                   class="space-y-4">
            <h3 [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'"
                class="text-lg sm:text-xl font-bold">Defina sua nova senha</h3>

            <div class="space-y-3">
              <!-- nova -->
              <div class="relative">
                <label class="sr-only" for="nova">Nova senha</label>
                <input [(ngModel)]="nova" id="nova" name="nova"
                       [type]="mostrar ? 'text' : 'password'" autocomplete="new-password"
                       placeholder="Nova senha"
                       [ngClass]="isDarkTheme
                         ? 'bg-[var(--bg-color)] text-[var(--text-color)] border-[var(--border-color)]'
                         : 'bg-white text-slate-900 border-slate-200 focus:border-transparent'"
                       class="w-full rounded-xl border
                              px-4 py-2.5 pr-12 outline-none
                              focus:ring-2 focus:ring-[var(--accent-yellow)]/80">
                <button type="button" (click)="toggle()"
                        [ngClass]="isDarkTheme ? 'hover:bg-[var(--table-row-hover)]' : 'hover:bg-slate-100'"
                        class="absolute right-2.5 top-1/2 -translate-y-1/2 p-2 rounded-md"
                        aria-label="Mostrar/ocultar senha">
                  <svg *ngIf="!mostrar" class="w-5 h-5" [ngClass]="isDarkTheme ? 'text-[var(--text-dim)]' : 'text-slate-500'"
                       viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8Z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                  <svg *ngIf="mostrar" class="w-5 h-5" [ngClass]="isDarkTheme ? 'text-[var(--text-dim)]' : 'text-slate-500'"
                       viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M3 3l18 18" />
                    <path d="M10.6 10.6a3 3 0 004.2 4.2" />
                    <path d="M9.9 4.2A10.7 10.7 0 0123 12s-4 8-11 8a11 11 0 01-6.3-2.1" />
                  </svg>
                </button>
              </div>

              <!-- barra de força -->
              <div class="mt-1">
                <div
                  [ngClass]="isDarkTheme ? 'bg-[var(--table-row-hover)]' : 'bg-slate-100'"
                  class="h-2 w-full rounded-full overflow-hidden">
                  <div class="h-2 rounded-full transition-all duration-300"
                       [ngClass]="strengthColorClass"
                       [style.width.%]="strengthPercent"></div>
                </div>
                <div class="mt-1 flex items-center justify-between text-xs">
                  <span [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-700'"
                        class="font-medium">
                    Força da senha: {{ strengthLabel }}
                  </span>
                  <span [ngClass]="isDarkTheme ? 'text-[var(--text-dim)]' : 'text-slate-400'">
                    {{ strengthPercent | number:'1.0-0' }}%
                  </span>
                </div>
                <div *ngIf="strengthTip"
                     [ngClass]="isDarkTheme ? 'text-[var(--text-dim)]' : 'text-slate-500'"
                     class="mt-1 text-[12px]">
                  Dica: {{ strengthTip }}
                </div>
              </div>

              <!-- confirmar -->
              <div>
                <label class="sr-only" for="confirma">Confirmar nova senha</label>
                <input [(ngModel)]="confirma" id="confirma" name="confirma"
                       [type]="mostrar ? 'text' : 'password'" autocomplete="new-password"
                       placeholder="Confirmar nova senha"
                       [ngClass]="isDarkTheme
                         ? 'bg-[var(--bg-color)] text-[var(--text-color)] border-[var(--border-color)]'
                         : 'bg-white text-slate-900 border-slate-200 focus:border-transparent'"
                       class="w-full rounded-xl border
                              px-4 py-2.5 outline-none
                              focus:ring-2 focus:ring-[var(--accent-yellow)]/80">
              </div>
            </div>

            <!-- checklist -->
            <ul class="mt-1.5 text-[13.5px] space-y-1.5"
                [ngClass]="isDarkTheme ? 'text-[var(--text-dim)]' : 'text-slate-500'">
              <li class="flex items-center gap-2">
                <span class="h-4 w-4 rounded-full"
                      [ngStyle]="{ 'background-color': minOk ? 'var(--success, #22c55e)' : (isDarkTheme ? 'rgba(148,163,184,.28)' : 'rgba(148,163,184,.25)') }"></span>
                Mínimo de <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">5 caracteres</b>
              </li>
              <li class="flex items-center gap-2">
                <span class="h-4 w-4 rounded-full"
                      [ngStyle]="{ 'background-color': numOk ? 'var(--success, #22c55e)' : (isDarkTheme ? 'rgba(148,163,184,.28)' : 'rgba(148,163,184,.25)') }"></span>
                Pelo menos <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">1 número</b>
              </li>
              <li class="flex items-center gap-2">
                <span class="h-4 w-4 rounded-full"
                      [ngStyle]="{ 'background-color': matchOk ? 'var(--success, #22c55e)' : (isDarkTheme ? 'rgba(148,163,184,.28)' : 'rgba(148,163,184,.25)') }"></span>
                As senhas <b [ngClass]="isDarkTheme ? 'text-[var(--text-color)]' : 'text-slate-900'">coincidem</b>
              </li>
            </ul>
          </section>
        </ng-container>
      </div>

      <ng-template #loading>
        <div [ngClass]="isDarkTheme ? 'text-[var(--text-dim)]' : 'text-slate-500'" class="px-6 py-6">Carregando...</div>
      </ng-template>

      <!-- FOOTER -->
      <div
        [ngClass]="isDarkTheme
          ? 'border-[var(--border-color)] bg-[var(--bg-card)]/85'
          : 'border-slate-200 bg-white/90'"
        class="px-6 sm:px-8 py-4 border-t
               flex items-center justify-between
               backdrop-blur-sm"
        *ngIf="(step$ | async) as step"
      >
        <ng-container *ngIf="(reason$ | async) as reason">
          <!-- bullets -->
          <div class="flex items-center gap-2">
            <span class="h-1.5 w-3 rounded-full"
                  [ngClass]="step >= 1
                    ? (isDarkTheme ? 'bg-[var(--text-color)]' : 'bg-slate-900')
                    : (isDarkTheme ? 'bg-[var(--table-row-hover)]' : 'bg-slate-200')"></span>
            <span class="h-1.5 w-3 rounded-full"
                  [ngClass]="step >= 2
                    ? (isDarkTheme ? 'bg-[var(--text-color)]' : 'bg-slate-900')
                    : (isDarkTheme ? 'bg-[var(--table-row-hover)]' : 'bg-slate-200')"></span>
            <span *ngIf="reason !== 'RESET_BY_ADMIN'" class="h-1.5 w-3 rounded-full"
                  [ngClass]="step >= 3
                    ? (isDarkTheme ? 'bg-[var(--text-color)]' : 'bg-slate-900')
                    : (isDarkTheme ? 'bg-[var(--table-row-hover)]' : 'bg-slate-200')"></span>
          </div>

          <!-- actions -->
          <div class="flex items-center gap-2">
            <button *ngIf="step>1"
                    (click)="prev()"
                    [ngClass]="isDarkTheme
                      ? 'border-[var(--border-color)] bg-[var(--bg-color)] hover:bg-[var(--table-row-hover)] text-[var(--text-color)]'
                      : 'border-slate-200 bg-white hover:bg-slate-100 text-slate-700'"
                    class="px-4 py-2 rounded-xl border transition">
              Voltar
            </button>

            <!-- botão avançar -->
            <button *ngIf="(reason === 'RESET_BY_ADMIN' ? step < 2 : step < 3)"
                    (click)="next()"
                    class="group px-5 py-2.5 rounded-xl
                           bg-[var(--accent-yellow)] hover:brightness-110
                           text-[#111827] inline-flex items-center gap-2 transition">
              <span class="font-medium">Avançar</span>
              <svg class="w-4 h-4 transition-transform group-hover:translate-x-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14" />
                <path d="M13 5l7 7-7 7" />
              </svg>
            </button>

            <!-- botão SALVAR (AGORA AMARELO) -->
           <button
  *ngIf="(reason === 'RESET_BY_ADMIN' && step===2) || (reason !== 'RESET_BY_ADMIN' && step===3)"
  (click)="salvar()"
  [disabled]="salvando || !minOk || !numOk || !matchOk"
  class="group px-5 py-2.5 rounded-xl
         font-semibold inline-flex items-center gap-2 transition
         bg-[var(--accent-yellow)] text-[#111827] hover:brightness-110
         disabled:bg-slate-200 disabled:text-slate-400
         disabled:shadow-none disabled:hover:brightness-100
         disabled:cursor-not-allowed"
>
  <svg *ngIf="salvando" class="w-4 h-4 animate-spin" viewBox="0 0 24 24" fill="none">
    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
    <path class="opacity-75" d="M4 12a8 8 0 018-8v4" stroke="currentColor" stroke-width="4" stroke-linecap="round"></path>
  </svg>
  <span>{{ salvando ? 'Salvando...' : 'Salvar' }}</span>
</button>


          </div>
        </ng-container>
      </div>
    </div>
  </div>

  <!-- SUCCESS -->
<!-- SUCCESS -->
<div *ngIf="showSuccess"
     class="fixed inset-0 z-[1200] grid place-items-center backdrop-blur-sm p-4 bg-black/30
            transition-opacity"
     [class.closing]="closing"
     (click)="closeNow()"
     aria-live="polite">

  <div
    (click)="$event.stopPropagation()"
    class="success-card pop-in relative w-[min(92vw,420px)] rounded-2xl
           px-6 py-8 shadow-2xl ring-1
           bg-[var(--bg-card)] border border-[var(--border-color)]
           text-center text-[var(--text-color)]">

    <!-- Ícone -->
    <div
      class="mx-auto flex h-16 w-16 items-center justify-center rounded-full
             bg-[var(--success,#22c55e)]/10
             ring-2 ring-[var(--success,#22c55e)]/30"
      style="animation:outlinePulse 1.4s ease-out infinite;"
    >
      <svg class="h-9 w-9 text-[var(--success,#22c55e)]"
           viewBox="0 0 24 24"
           fill="none"
           stroke="currentColor"
           stroke-width="2">
        <circle cx="12" cy="12" r="9" opacity="0.25" stroke="currentColor" fill="none"></circle>
        <path d="M7 12l3 3 7-7" class="check-anim" stroke="currentColor"></path>
      </svg>
    </div>

    <!-- Título -->
    <h3 class="mt-4 text-xl font-bold text-[var(--text-color)]">
      Senha alterada!
    </h3>

    <!-- Mensagem -->
    <p class="mt-1 text-sm text-[var(--text-dim)]">
      Tudo certo. Redirecionando…
    </p>

    <!-- Contagem regressiva -->
    <div class="mt-5 flex items-center justify-center gap-2 text-xs text-[var(--text-dim)]">
      <span class="h-2 w-2 rounded-full bg-[var(--success,#22c55e)] animate-ping"></span>
      <span>Fechando em {{ countdown }}s</span>
    </div>

    <!-- Botão -->
    <button
      class="mt-6 px-4 py-2 rounded-lg bg-[var(--success,#22c55e)]
             text-white font-medium hover:brightness-110 transition"
      (click)="closeNow()">
      Fechar agora
    </button>
  </div>
</div>

  `
})
export class ForcePasswordChangeModalComponent implements OnInit, OnDestroy {
  nova = '';
  confirma = '';
  mostrar = false;
  salvando = false;

  showSuccess = false;
  closing = false;
  countdown = 4;
  private countdownId: any;
  private closeTimer: any;

  // começa CLARO
  isDarkTheme = false;

  private subs = new Subscription();

  get hasNova()     { return (this.nova ?? '').length > 0; }
  get hasConfirma() { return (this.confirma ?? '').length > 0; }
  get minOk()       { return (this.nova ?? '').length >= 5; }
  get numOk()       { return /\d/.test(this.nova ?? ''); }
  get matchOk()     { return this.hasNova && this.hasConfirma && this.nova === this.confirma; }

  private hasUpper(p: string) { return /[A-Z]/.test(p); }
  private hasLower(p: string) { return /[a-z]/.test(p); }
  private hasDigit(p: string) { return /\d/.test(p); }
  private hasSymbol(p: string){ return /[^A-Za-z0-9]/.test(p); }
  private hasRepeat(p: string){ return /(.)\1{2,}/.test(p); }
  private hasCommon(p: string){
    return /(1234|123456|qwerty|asdf|senha|password|admin|welcome)/i.test(p);
  }
  private hasSeq(p: string){
    const sequences = ['abc','abcd','qwe','qwerty','012','123','234','345','456','567','678','789','890','098','987','zyx'];
    const s = p.toLowerCase();
    return sequences.some(seq => s.includes(seq));
  }

  get strengthScore(): number {
    const p = (this.nova ?? '');
    if (!p) return 0;

    let lp = 0;
    if (p.length >= 15) lp = 4;
    else if (p.length >= 11) lp = 3;
    else if (p.length >= 8) lp = 2;
    else if (p.length >= 5) lp = 1;

    const variety = [this.hasLower(p), this.hasUpper(p), this.hasDigit(p), this.hasSymbol(p)]
      .filter(Boolean).length - 1;

    let score = lp + Math.max(0, variety);

    if (this.hasCommon(p) || this.hasSeq(p)) score -= 2;
    if (this.hasRepeat(p)) score -= 1;
    if (this.firstName && this.firstName.length >= 3 && p.toLowerCase().includes(this.firstName.toLowerCase())) score -= 1;

    return Math.max(0, Math.min(4, score));
  }

  get strengthPercent(): number {
    return [0, 25, 50, 75, 100][this.strengthScore] ?? 0;
  }

  get strengthLabel(): string {
    return ['Muito fraca', 'Fraca', 'Ok', 'Forte', 'Excelente'][this.strengthScore] ?? '—';
  }

  get strengthColorClass(): string {
    return [
      'bg-[var(--danger,#ef4444)]',
      'bg-[var(--accent-yellow,#facc15)]',
      'bg-[var(--accent-yellow,#facc15)]',
      'bg-[var(--success,#22c55e)]',
      'bg-[var(--success,#22c55e)]'
    ][this.strengthScore] ?? (this.isDarkTheme ? 'bg-[var(--table-row-hover)]' : 'bg-slate-200');
  }

  get strengthTip(): string | null {
    const p = (this.nova ?? '');
    if (!p) return null;

    if (p.length < 8) return 'Use pelo menos 8 caracteres.';
    const miss = [];
    if (!this.hasUpper(p)) miss.push('maiúsculas');
    if (!this.hasLower(p)) miss.push('minúsculas');
    if (!this.hasDigit(p)) miss.push('números');
    if (!this.hasSymbol(p)) miss.push('símbolos');
    if (miss.length) return 'Adicione ' + miss.join(', ') + '.';

    if (this.hasCommon(p) || this.hasSeq(p)) return 'Evite padrões comuns (ex.: 1234, qwerty).';
    if (this.hasRepeat(p)) return 'Evite repetir o mesmo caractere muitas vezes.';
    if (this.firstName && p.toLowerCase().includes(this.firstName.toLowerCase())) return 'Evite usar seu nome na senha.';

    return this.strengthScore >= 3 ? null : 'Combine letras, números e símbolos para fortalecer.';
  }

  get reason$() { return this.prompt.reason$; }
  get step$()   { return this.prompt.step$; }

  firstName = '';

  constructor(
    public  prompt: PasswordPromptService,
    private users:  UsersService,
    private toast:  ToastService,
    private auth:   AuthService,
    private router: Router
    , private modalScroll: ModalScrollService
  ) {
    const nome = this.auth.getCurrentUser()?.nome || '';
    this.firstName = nome.split(' ')[0] || '';
  }

  ngOnInit(): void {
    // começa CLARO
    this.isDarkTheme = false;

    // lock background scrolling while this modal is shown
    this.modalScroll.lock();

    const sub = this.prompt.reason$.subscribe((reason) => {
      if (reason === 'RESET_BY_ADMIN') {
        this.isDarkTheme = this.detectDarkTheme();
      } else {
        this.isDarkTheme = false;
      }
    });
    this.subs.add(sub);
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.modalScroll.unlock();
  }

  private detectDarkTheme(): boolean {
    if (typeof document === 'undefined') return false;
    return document.body.classList.contains('dark-theme') ||
           document.documentElement.classList.contains('dark-theme');
  }

  @HostListener('document:keydown.escape')
  onEsc() {
    if (this.showSuccess) { this.closeNow(); return; }
    if (this.prompt.getStepSnapshot() < this.prompt.getStepMaxSnapshot()) return;
  }

  @HostListener('document:keydown.enter', ['$event'])
  onEnter(e: KeyboardEvent) {
    if (this.showSuccess) { this.closeNow(); return; }
    e.preventDefault();
    const step = this.prompt.getStepSnapshot();
    const max  = this.prompt.getStepMaxSnapshot();
    if (step < max) this.next();
    else this.salvar();
  }

  toggle() { this.mostrar = !this.mostrar; }

  private senhaValida(s: string): boolean {
    return typeof s === 'string' && s.length >= 5 && /\d/.test(s);
  }

  next() { this.prompt.next(); }
  prev() { this.prompt.prev(); }

  private startSuccess(seconds = 4) {
    this.countdown = seconds;
    this.showSuccess = true;

    this.auth.loadUserFromBackend?.().subscribe({ complete: () => {} });

    this.countdownId = setInterval(() => {
      this.countdown--;
      if (this.countdown <= 0) this.closeNow();
    }, 1000);

    this.closeTimer = setTimeout(() => this.closeNow(), seconds * 1000);
  }

  closeNow() {
    if (this.closing) return;
    this.closing = true;

    setTimeout(() => {
      this.showSuccess = false;
      this.closing = false;

      if (this.countdownId) clearInterval(this.countdownId);
      if (this.closeTimer) clearTimeout(this.closeTimer);

      this.prompt.close();
      this.router.navigate(['/dashboard'], { replaceUrl: true });
    }, 220);
  }

  salvar() {
    const nova = (this.nova || '').trim();
    const conf = (this.confirma || '').trim();

    if (!this.senhaValida(nova)) {
      this.toast.warning('A senha deve ter pelo menos 5 caracteres e conter ao menos 1 número.');
      return;
    }
    if (nova !== conf) {
      this.toast.warning('As senhas não conferem.');
      return;
    }

    this.salvando = true;
    this.users.trocarSenha(nova, '').subscribe({
      next: () => {
        this.salvando = false;
        this.toast.success('Senha alterada com sucesso!');
        this.auth.clearPromptPasswordChangeFlag?.();
        this.startSuccess(4);
      },
      error: (e) => {
        this.salvando = false;
        const raw = e?.error ?? e;
        const msg = raw?.mensagem || raw?.message || 'Falha ao alterar senha.';
        this.toast.error(msg);
      }
    });
  }
}
