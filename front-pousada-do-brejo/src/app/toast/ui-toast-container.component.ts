import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from './toast.service';

@Component({
  selector: 'ui-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
  <div class="fixed top-4 right-4 z-[9999] w-[calc(100vw-2rem)] sm:w-[380px] pointer-events-none space-y-3">
    <div *ngFor="let t of toast.toasts$ | async; trackBy: trackById"
         class="pointer-events-auto relative rounded-2xl bg-white text-slate-800 border border-slate-200
                shadow-[0_12px_24px_-10px_rgba(2,6,23,.25)] animate-[toastIn_.16s_ease] overflow-hidden">

      <div class="absolute inset-y-0 left-0 w-1.5" [ngClass]="barTone(t.type)"></div>

      <div class="pl-5 pr-3 py-3 sm:py-3.5 flex items-start gap-3">
        <div class="mt-0.5 shrink-0 rounded-md p-1.5" [ngClass]="chipTone(t.type)">
          <svg *ngIf="t.type==='warning'" class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
            <path d="M8.257 3.099c.765-1.36 2.72-1.36 3.485 0l6.516 11.59c.75 1.334-.213 2.986-1.742 2.986H3.483c-1.53 0-2.492-1.652-1.743-2.986L8.257 3.1zM11 14a1 1 0 10-2 0 1 1 0 002 0zm-1-2a1 1 0 01-1-1V8a1 1 0 112 0v3a1 1 0 01-1 1z"/>
          </svg>
          <svg *ngIf="t.type==='success'" class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
            <path d="M16.707 5.293a1 1 0 010 1.414l-7.364 7.364a1 1 0 01-1.414 0L3.293 9.435a1 1 0 111.414-1.414l3.02 3.02 6.657-6.657a1 1 0 011.323-.091z"/>
          </svg>
          <svg *ngIf="t.type==='error'" class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
            <path d="M10 18a8 8 0 100-16 8 8 0 000 16zm-1-5h2v2H9v-2zm0-8h2v6H9V5z"/>
          </svg>
          <svg *ngIf="t.type==='info'" class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
            <path d="M9 9h2v6H9V9zm1-4a1.5 1.5 0 100 3 1.5 1.5 0 000-3z"/>
          </svg>
        </div>

        <div class="min-w-0 grow">
          <p class="font-semibold text-slate-900 leading-5">{{ titleOf(t.type) }}</p>
          <p class="text-[13.5px] text-slate-600 leading-snug mt-0.5">{{ t.message }}</p>
        </div>

        <button (click)="toast.remove(t.id)"
                class="p-1 rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition"
                aria-label="Fechar">✕</button>
      </div>
    </div>
  </div>
  `,
  styles: [`
    @keyframes toastIn { from { opacity:.6; transform: translateY(-6px) } to { opacity:1; transform:none } }
  `]
})
export class UiToastContainerComponent {
  constructor(public toast: ToastService) {}
  trackById = (_: number, t: any) => t.id;

  titleOf(type: 'success'|'error'|'warning'|'info') {
    switch (type) {
      case 'warning': return 'Atenção';
      case 'success': return 'Sucesso';
      case 'error':   return 'Erro';
      default:        return 'Informação';
    }
  }

  barTone(type: 'success'|'error'|'warning'|'info') {
    switch (type) {
      case 'warning': return 'bg-amber-400';
      case 'success': return 'bg-emerald-500';
      case 'error':   return 'bg-rose-500';
      default:        return 'bg-sky-500';
    }
  }

  chipTone(type: 'success'|'error'|'warning'|'info') {
    switch (type) {
      case 'warning': return 'bg-amber-100 text-amber-600';
      case 'success': return 'bg-emerald-100 text-emerald-600';
      case 'error':   return 'bg-rose-100 text-rose-600';
      default:        return 'bg-sky-100 text-sky-600';
    }
  }
}
