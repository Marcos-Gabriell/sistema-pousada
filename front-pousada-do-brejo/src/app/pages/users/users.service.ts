import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { AuthService } from '../../services/login/auth.service';

export type Role = 'DEV' | 'ADMIN' | 'GERENTE';

export interface AvatarView {
  mode: 'PHOTO' | 'COLOR' | 'PRESET';
  color?: string | null;
  gender?: 'MASCULINO' | 'FEMININO' | 'NEUTRO' | null;
  dataUrl?: string | null;
  version: number;
}

export interface Usuario {
  id: number;
  codigo?: string | null;
  nome: string;
  email?: string | null;
  username?: string | null;
  numero?: string | null;
  role: Role;
  ativo: boolean;

  criadoEm?: string | null;
  criadoPorId?: number | null;
  criadoPorNome?: string | null;

  bootstrapAdmin?: boolean;
  avatar?: AvatarView;
  inativadoEm?: string | null;

  diasRestantesExclusao?: number | null;
  autoDeleteAt?: string | null;
  tema?: 'claro' | 'escuro';

  // ✅ SOMENTE ONLINE
  online?: boolean | null;
}

export interface PaginacaoUsuarios {
  conteudo: Usuario[];
  pagina: number;
  tamanho: number;
  totalPaginas: number;
  totalElementos: number;
}

export interface CriarUsuarioPayload {
  nome: string;
  email?: string | null;
  username?: string | null;
  numero?: string | null;
  role: Role;
  ativo?: boolean;
}

export interface AtualizarUsuarioPayload {
  nome?: string;
  email?: string;
  username?: string;
  numero?: string;
  role?: Role;
  ativo?: boolean;
}

export interface ResetSenhaResponse {
  mensagem: string;
  senhaTemporaria?: string;
  forceLogout?: boolean;
  alvoId?: number | string;
}

export interface UsuarioCriadoResponse {
  id: number;
  codigo?: string | null;
  nome: string;
  username?: string | null;
  email?: string | null;
  numero?: string | null;
  role: Role;
  ativo: boolean;
  mensagem?: string;
  senhaTemporaria?: string;
}

export interface AlterarStatusResponse extends Usuario {
  mensagem: string;
  forceLogout?: boolean;
  alvoId?: number | string;
}

export interface AuthMeResponse {
  id: number;
  nome?: string | null;
  username?: string | null;
  email?: string | null;
  numero?: string | null;
  role: Role;
  ativo?: boolean;
  tema?: 'claro' | 'escuro';

  mustChangePassword?: boolean | null;
  pwdChangeReason?: 'FIRST_LOGIN' | 'RESET_BY_ADMIN' | 'UNKNOWN' | null;

  token?: string | null;
}

@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly baseUrl = 'http://localhost:8080/api/usuarios';
  private readonly rootApi = 'http://localhost:8080/api';

  constructor(
    private http: HttpClient,
    private auth: AuthService
  ) {}

  private authHeaders(): HttpHeaders | undefined {
    let t = this.auth.getToken() || '';
    t = t.replace(/^\s*Bearer\s+/i, '').trim();
    return t ? new HttpHeaders({ Authorization: `Bearer ${t}` }) : undefined;
  }

  listar(pagina = 1, tamanho = 10, busca = '', status = '', perfil = ''): Observable<PaginacaoUsuarios> {
    let params = new HttpParams()
      .set('pagina', String(pagina))
      .set('tamanho', String(tamanho));

    if (busca)  params = params.set('q', busca);
    if (status) params = params.set('status', status);
    if (perfil) params = params.set('perfil', perfil);

    const headers = this.authHeaders();

    return this.http.get<PaginacaoUsuarios>(this.baseUrl, {
      params,
      headers: headers ?? undefined
    });
  }

  criar(payload: CriarUsuarioPayload): Observable<UsuarioCriadoResponse> {
    const body: any = {
      nome: payload.nome,
      username: payload.username ?? null,
      email: payload.email ?? null,
      numero: payload.numero ?? null,
      role: payload.role,
      ativo: payload.ativo ?? true
    };

    const headers = this.authHeaders();

    return this.http.post<UsuarioCriadoResponse>(this.baseUrl, body, {
      headers: headers ?? undefined
    });
  }

  atualizar(id: number, payload: AtualizarUsuarioPayload): Observable<Usuario> {
    const headers = this.authHeaders();
    return this.http.put<Usuario>(`${this.baseUrl}/${id}`, payload, {
      headers: headers ?? undefined
    });
  }

  excluir(id: number): Observable<void> {
    const headers = this.authHeaders();
    return this.http.delete<void>(`${this.baseUrl}/${id}`, {
      headers: headers ?? undefined
    });
  }

  alternarStatus(id: number, ativo: boolean): Observable<AlterarStatusResponse> {
    const headers = this.authHeaders();
    return this.http.patch<AlterarStatusResponse>(
      `${this.baseUrl}/${id}/status`,
      { ativo },
      { headers: headers ?? undefined }
    );
  }

  resetarSenha(id: number): Observable<ResetSenhaResponse> {
    const headers = this.authHeaders();
    return this.http.post<ResetSenhaResponse>(
      `${this.baseUrl}/${id}/reset-senha`,
      {},
      { headers: headers ?? undefined }
    );
  }

  listarPerfis(): Observable<string[]> {
    const headers = this.authHeaders();
    return this.http.get<string[]>(`${this.baseUrl}/perfis`, {
      headers: headers ?? undefined
    });
  }

  me(): Observable<AuthMeResponse> {
    const headers = this.authHeaders();
    return this.http.get<AuthMeResponse>(`${this.baseUrl}/me`, {
      headers: headers ?? undefined
    });
  }

  meuPerfil(): Observable<{ role: Role; bootstrapAdmin?: boolean }> {
    return this.me().pipe(
      map(u => ({
        role: u.role,
        bootstrapAdmin: false
      }))
    );
  }

  trocarSenha(novaSenha: string, senhaAtual: string = ''): Observable<{ mensagem: string }> {
    const headers = this.authHeaders();
    const body = {
      senhaAtual: (senhaAtual ?? '').trim(),
      novaSenha:  (novaSenha  ?? '').trim(),
      confirmarSenha: (novaSenha ?? '').trim()
    };
    return this.http.post<{ mensagem: string }>(
      `${this.rootApi}/auth/reset-password`,
      body,
      { headers: headers ?? undefined }
    );
  }

  getAvatar(id: number): Observable<AvatarView> {
    const headers = this.authHeaders();
    return this.http.get<AvatarView>(`${this.baseUrl}/${id}/avatar`, {
      headers: headers ?? undefined
    });
  }

  obter(id: number): Observable<Usuario> {
    const headers = this.authHeaders();
    return this.http.get<Usuario>(`${this.baseUrl}/${id}`, {
      headers: headers ?? undefined
    });
  }

  atualizarTema(id: number, tema: 'claro' | 'escuro'): Observable<any> {
    const headersAuth = this.authHeaders() ?? new HttpHeaders();
    const headers = headersAuth.set('Content-Type', 'text/plain; charset=UTF-8');

    return this.http.patch<any>(
      `${this.baseUrl}/${id}/tema`,
      tema,
      { headers }
    );
  }
}
