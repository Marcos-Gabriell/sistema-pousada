
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import type { PwdReason } from './auth.service'; 

@Injectable({ providedIn: 'root' })
export class PasswordPromptService {
  private _open$    = new BehaviorSubject<boolean>(false);
  private _reason$  = new BehaviorSubject<PwdReason>('UNKNOWN');
  private _step$    = new BehaviorSubject<number>(1);
  private _stepMax$ = new BehaviorSubject<number>(3);

  open$    = this._open$.asObservable();
  reason$  = this._reason$.asObservable();
  step$    = this._step$.asObservable();
  stepMax$ = this._stepMax$.asObservable();

  openWith(reason: PwdReason) {
    this._reason$.next(reason);
    this._stepMax$.next(reason === 'RESET_BY_ADMIN' ? 2 : 3); 
    this._step$.next(1);
    this._open$.next(true);
  }

  open()  { this.openWith('UNKNOWN'); }
  close() { this._open$.next(false); }

  next() { const s = this._step$.value, m = this._stepMax$.value; if (s < m) this._step$.next(s + 1); }
  prev() { const s = this._step$.value; if (s > 1) this._step$.next(s - 1); }

  getStepSnapshot()    { return this._step$.value; }
  getStepMaxSnapshot() { return this._stepMax$.value; }
}
