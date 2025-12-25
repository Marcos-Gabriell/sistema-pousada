import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Toast, ToastType } from './toast.model';

@Injectable({ providedIn: 'root' })
export class ToastService {
  private _toasts$ = new BehaviorSubject<Toast[]>([]);
  toasts$ = this._toasts$.asObservable();

  private readonly DEDUP_WINDOW = 3000;
  private _recent = new Map<string, number>();

  private push(type: ToastType, message: string, duration = 2500) {
    try {
      const key = `${type}|${message}`;
      const now = Date.now();
      const last = this._recent.get(key) ?? 0;
      if (last && (now - last) < this.DEDUP_WINDOW) {
        return;
      }
      this._recent.set(key, now);
      if (this._recent.size > 200) {
        const cutoff = now - (this.DEDUP_WINDOW * 4);
        for (const [k, ts] of Array.from(this._recent.entries())) {
          if (ts < cutoff) this._recent.delete(k);
        }
      }

      const toast: Toast = { id: crypto.randomUUID(), type, message, duration };
      this._toasts$.next([...this._toasts$.value, toast]);
      if (duration > 0) setTimeout(() => this.remove(toast.id), duration);
    } catch (err) {
      const fallbackId = 't_' + Math.random().toString(36).slice(2, 9);
      const toast: Toast = { id: fallbackId, type, message, duration };
      this._toasts$.next([...this._toasts$.value, toast]);
      if (duration > 0) setTimeout(() => this.remove(toast.id), duration);
    }
  }

  remove(id: string) {
    this._toasts$.next(this._toasts$.value.filter(t => t.id !== id));
  }

  success(m: string, ms?: number) { this.push('success', m, ms); }
  error(m: string, ms?: number)   { this.push('error', m, ms ?? 4000); }
  warning(m: string, ms?: number) { this.push('warning', m, ms); }
  info(m: string, ms?: number)    { this.push('info', m, ms); }
}
