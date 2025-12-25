import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError, map } from 'rxjs';

export type TipoHospedagem = 'COMUM' | 'PREFEITURA' | 'CORPORATIVO';

export interface HospedagemResponse {
  id: number;
  tipo: TipoHospedagem;
  nome: string;
  cpf: string | null;
  dataEntrada: string;
  dataSaida: string | null;
  numeroDiarias: number | null;
  valorDiaria: number | null;
  valorTotal: number | null;
  formaPagamento: string | null;
  observacoes: string | null;
  numeroQuarto: string | null;
  ocupado: boolean;
  status: 'Ativo' | 'Inativo';
  codigoHospedagem: string | null;
  criadoPor: string | null;
  criadoEm?: string | null;
}

export interface CheckinPayload {
  nome: string;
  cpf?: string | null;
  numeroQuarto: string;
  numeroDiarias: number;
  valorDiaria: number;
  formaPagamento: string;
  observacoes?: string;
  tipo: TipoHospedagem;
}

export interface EditarHospedagemDTO {
  numeroQuarto?: string;
  numeroDiarias?: number;
  formaPagamento?: string;
  observacoes?: string;
  tipo?: TipoHospedagem;
}

export interface CheckoutPayload {
  numeroQuarto: string;
  descricao: string;
}

export interface QuartoCatalogo {
  numero: string;
  tipo?: string;
  capacidade?: number;
}

@Injectable({ providedIn: 'root' })
export class HospedagemService {
  private readonly API = 'http://localhost:8080/api';
  private readonly API_HOSPEDAGENS = `${this.API}/hospedagens`;
  private readonly API_QUARTOS = `${this.API}/quartos`;

  constructor(private http: HttpClient) {}

  private authOptions(extra?: { params?: HttpParams }) {
    const raw = localStorage.getItem('token') || '';
    const token = raw.replace(/^"|"$/g, '').trim();

    const headers = token
      ? ({ Authorization: `Bearer ${token}` } as Record<string, string>)
      : undefined;

    return { headers, ...(extra ?? {}) };
  }

  private toYMD(dateLike: Date | string): string {
    if (!dateLike) return '';
    if (dateLike instanceof Date) {
      const y = dateLike.getFullYear();
      const m = String(dateLike.getMonth() + 1).padStart(2, '0');
      const d = String(dateLike.getDate()).padStart(2, '0');
      return `${y}-${m}-${d}`;
    }
    const s = String(dateLike);
    return s.includes('T') ? s.split('T')[0] : s;
  }

  listarHospedagens(params?: {
    nome?: string;
    tipo?: string;
    dataEntrada?: string;
  }): Observable<HospedagemResponse[]> {
    let httpParams = new HttpParams();
    if (params?.nome)        httpParams = httpParams.set('nome', params.nome);
    if (params?.tipo)        httpParams = httpParams.set('tipo', params.tipo);
    if (params?.dataEntrada) httpParams = httpParams.set('dataEntrada', params.dataEntrada);

    return this.http
      .get<any>(this.API_HOSPEDAGENS, this.authOptions({ params: httpParams }))
      .pipe(map(res => (Array.isArray(res) ? (res as HospedagemResponse[]) : [])));
  }

  filtrarRelatorio(opts: {
    dataInicio?: string;
    dataFim?: string;
    tipo?: string;
    nome?: string;
    ativo?: boolean;
    incluirCanceladas?: boolean;
  }): Observable<HospedagemResponse[]> {
    const clean = (v: unknown) =>
      v !== undefined && v !== null && String(v) !== '';

    let params = new HttpParams();
    Object.entries(opts || {}).forEach(([k, v]) => {
      if (clean(v)) params = params.set(k, String(v));
    });

    return this.http
      .get<any>(
        `${this.API_HOSPEDAGENS}/relatorios/hospedagens`,
        this.authOptions({ params })
      )
      .pipe(map(r => (Array.isArray(r) ? (r as HospedagemResponse[]) : [])));
  }

  realizarCheckin(payload: CheckinPayload): Observable<HospedagemResponse> {
    return this.http.post<HospedagemResponse>(
      `${this.API_HOSPEDAGENS}/checkin`,
      payload,
      this.authOptions()
    );
  }

  editarHospedagem(id: number, dto: EditarHospedagemDTO): Observable<HospedagemResponse> {
    return this.http.put<HospedagemResponse>(
      `${this.API_HOSPEDAGENS}/${id}`,
      dto,
      this.authOptions()
    );
  }

  realizarCheckout(payload: { numeroQuarto: string; descricao: string }): Observable<HospedagemResponse> {
    return this.http.post<HospedagemResponse>(
      `${this.API_HOSPEDAGENS}/checkout`,
      payload,
      this.authOptions()
    );
  }

  // Disponíveis por período (fallback/apoio)
  getQuartosDisponiveis(opts?: { dataEntrada?: string | Date; dataSaida?: string | Date }): Observable<{ numero: string }[]> {
    const hoje = this.toYMD(new Date());
    const amanha = this.toYMD(new Date(Date.now() + 24 * 60 * 60 * 1000));

    const de  = this.toYMD(opts?.dataEntrada || hoje);
    const ate = this.toYMD(opts?.dataSaida   || amanha);

    const p1 = new HttpParams()
      .set('dataEntrada', de)
      .set('dataSaida', ate);

    return this.http
      .get<{ numero: string }[]>(
        `${this.API_QUARTOS}/disponiveis`,
        this.authOptions({ params: p1 })
      )
      .pipe(
        catchError((err: HttpErrorResponse) => {
          if (err.status !== 400) {
            return throwError(() => err);
          }
          const p2 = new HttpParams()
            .set('dataInicio', de)
            .set('dataFim', ate);
          return this.http.get<{ numero: string }[]>(
            `${this.API_QUARTOS}/disponiveis`,
            this.authOptions({ params: p2 })
          );
        })
      );
  }

  // ✅ Catálogo completo (nº + tipo) para rotular “Individual/Duplo/Triplo”
  getQuartosCatalogo(): Observable<QuartoCatalogo[]> {
    return this.http
      .get<any>(`${this.API_QUARTOS}`, this.authOptions())
      .pipe(map(r => (Array.isArray(r) ? (r as QuartoCatalogo[]) : [])));
  }

  excluirHospedagem(id: number): Observable<void> {
  return this.http.delete<void>(`${this.API_HOSPEDAGENS}/${id}`, this.authOptions());
}

}
