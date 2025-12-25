// src/app/pages/perfil/perfil.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../../services/login/auth.service';

export interface CurrentUser {
  id: number;
  roles?: string[];
  nome: string;
  email: string;
  numero?: string | null;
  username?: string | null;
  avatar?: AvatarView;
  avatarUrl?: string | null;
}

export interface AvatarView {
  mode: 'PHOTO' | 'COLOR';
  color?: string | null;     // cor sólida
  dataUrl?: string | null;   // data:image/jpeg;base64,...
  gradient?: string | null;
  // version pode não estar sempre presente do backend; opcionaliza para evitar erros
  version?: number;
}


export interface UpdateSelfDto {
  nome: string;
  email: string;
  numero: string | null;
  username: string | null;
}

export interface ChangePasswordDto {
  senhaAtual?: string;
  novaSenha: string;
  confirmarSenha: string;
}

@Injectable({ providedIn: 'root' })
export class PerfilService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private readonly API_URL = 'http://localhost:8080/api/usuarios';

  private authOnlyHeaders(): { headers?: HttpHeaders } {
    let t = this.auth.getToken?.() || '';
    t = t.replace(/^\s*Bearer\s+/i, '').trim();
    return t ? { headers: new HttpHeaders({ Authorization: `Bearer ${t}` }) } : {};
  }

  private jsonAuthHeaders(): { headers?: HttpHeaders } {
    let t = this.auth.getToken?.() || '';
    t = t.replace(/^\s*Bearer\s+/i, '').trim();
    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      ...(t ? { Authorization: `Bearer ${t}` } : {})
    });
    return { headers };
  }

  // ===== Usuário =====
  getMe(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${this.API_URL}/me`, this.authOnlyHeaders());
  }

  atualizarMe(dto: UpdateSelfDto): Observable<CurrentUser> {
    return this.http.put<CurrentUser>(`${this.API_URL}/me`, dto, this.jsonAuthHeaders());
  }

  // ===== Avatar =====
  getMeuAvatar(): Observable<AvatarView> {
    return this.http.get<AvatarView>(`${this.API_URL}/me/avatar`, this.authOnlyHeaders());
  }

  /** Envia a foto em base64 (dataURL completo) */
  enviarAvatarBase64(dataUrl: string): Observable<AvatarView | { mensagem?: string }> {
    // mantenha o endpoint que você já expôs no backend
    return this.http.post<AvatarView | { mensagem?: string }>(
      `${this.API_URL}/me/avatar/base64`,
      { dataUrl },
      this.jsonAuthHeaders()
    );
  }

  /** Define modo COLOR + cor sólida */
setAvatarColor(hex: string) {
  return this.http.patch<AvatarView>(
    `${this.API_URL}/me/avatar/prefs`,
    { avatarMode: 'COLOR', avatarColor: hex },
    this.jsonAuthHeaders()
  );
}

  /** Alterna para modo PHOTO (usando a última foto salva) */

setAvatarPhotoMode() {
  return this.http.patch<AvatarView>(
    `${this.API_URL}/me/avatar/prefs`,
    { avatarMode: 'PHOTO' },
    this.jsonAuthHeaders()
  );
}

setAvatarGradient(key: string) {
  return this.http.patch<AvatarView>(
    `${this.API_URL}/me/avatar/prefs`,
    { avatarMode: 'COLOR', avatarGradient: key }, // ✅ aqui é COLOR
    this.jsonAuthHeaders()
  );
}
  // ===== Troca de senha =====
  trocarSenha(dto: ChangePasswordDto): Observable<{ mensagem: string }> {
    return this.http.patch<{ mensagem: string }>(
      `${this.API_URL}/me/trocar-senha`,
      dto,
      this.jsonAuthHeaders()
    );
  }
}
