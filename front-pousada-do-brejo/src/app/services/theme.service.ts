import { Injectable, signal } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { UsersService as UsuarioService } from '../pages/users/users.service';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private _tema = signal<'claro' | 'escuro'>('claro');
  private _userId: number | null = null;

  public tema$ = new BehaviorSubject<'claro' | 'escuro'>('claro');

  constructor(private usuarioService: UsuarioService) {
    this.carregarTemaInicial();
  }

  private carregarTemaInicial(): void {
    const temaCache = localStorage.getItem('tema');
    const temaInicial: 'claro' | 'escuro' =
      temaCache === 'escuro' ? 'escuro' : 'claro';

    this.applyTheme(temaInicial, false); 
    
  }

  initFromUser(userId: number, temaPreferido: string | null | undefined): void {
    this._userId = userId;

    let temaFinal: 'claro' | 'escuro' = 'claro';

    if (temaPreferido === 'escuro' || temaPreferido === 'claro') {
      temaFinal = temaPreferido;
    } else {
      const cache = localStorage.getItem('tema');
      if (cache === 'escuro' || cache === 'claro') {
        temaFinal = cache;
      }
    }

    this.applyTheme(temaFinal, false);
  }

  forceLightForLogin(): void {
    this._userId = null;
    this.applyTheme('claro', false);
  }

  get tema(): 'claro' | 'escuro' {
    return this._tema();
  }

  isEscuro(): boolean {
    return this._tema() === 'escuro';
  }

  toggle(): void {
    const novoTema = this._tema() === 'claro' ? 'escuro' : 'claro';
    this.applyTheme(novoTema, true);
  }

  private applyTheme(tema: 'claro' | 'escuro', salvarBackend: boolean): void {
    this._tema.set(tema);
    this.tema$.next(tema);

    document.body.classList.toggle('dark-theme', tema === 'escuro');

    localStorage.setItem('tema', tema);

    if (salvarBackend && this._userId != null) {
      this.persistBackend();
    }
  }

  private persistBackend(): void {
    if (this._userId == null) return;

    this.usuarioService.atualizarTema(this._userId, this._tema()).subscribe({
      next: () => {},
      error: (err) => {}
    });
  }

  clearUser(): void {
    this._userId = null;
  }
}
