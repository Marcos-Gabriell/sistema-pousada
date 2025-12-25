import { Injectable } from '@angular/core';
import {
  HttpClient,
  HttpParams,
  HttpHeaders,
  HttpErrorResponse,
} from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';

export type TipoLancamento = 'ENTRADA' | 'SAIDA';
export type OrigemLancamento = 'MANUAL' | 'HOSPEDAGEM' | string;

export interface Lancamento {
  id: number;
  codigo?: string | null;
  tipo: TipoLancamento;
  origem: OrigemLancamento;
  referenciaId?: number | null;
  data: string;
  criadoEm?: string | null;
  valor: number;
  formaPagamento?: string | null;
  descricao: string;
  criadoPorId?: number | null;
  criadoPorNome?: string | null;

  // edição
  edicoes?: number | null;
  editadoEm?: string | null;
  editadoPorId?: number | null;
  editadoPorNome?: string | null;

  // cancelamento
  cancelado?: boolean;
  canceladoEm?: string | null;
  canceladoPorId?: number | null;
  canceladoPorNome?: string | null;
  canceladoPorPerfil?: string | null;
  canceladoMotivo?: string | null;
}

export type CriarLancamentoPayload = {
  tipo: TipoLancamento;
  origem?: OrigemLancamento;
  valor: number | string;
  formaPagamento?: string;
  descricao: string;
  referenciaId?: number;
};

export type AtualizarLancamentoPayload = {
  valor?: number | string;
  formaPagamento?: string;
  descricao?: string;
};

@Injectable({ providedIn: 'root' })
export class FinanceiroService {
  private readonly API = 'http://localhost:8080/api';
  private readonly API_LANC = `${this.API}/financeiro/lancamentos`;

  constructor(private http: HttpClient) {}

  private authOptions(extra?: { params?: HttpParams; headers?: HttpHeaders }) {
    try {
      const raw = localStorage.getItem('token') || '';
      const token = raw.replace(/^"|"$/g, '').trim();
      const baseHeaders: Record<string, string> = token
        ? { Authorization: `Bearer ${token}` }
        : {};
      const headers = new HttpHeaders({
        ...baseHeaders,
        ...(extra?.headers ? (extra.headers as any) : {}),
      });
      return { headers, ...(extra ?? {}) };
    } catch {
      return { ...(extra ?? {}) };
    }
  }

  private handleError(error: HttpErrorResponse) {
    let errorMessage = 'Erro desconhecido no financeiro.';
    if (error.error instanceof ErrorEvent) {
      return throwError(() => new Error(`Erro: ${error.error.message}`));
    }
    const backendMsg =
      (typeof error.error === 'string' && error.error) ||
      (typeof error.error === 'object' &&
        (error.error?.message ||
          error.error?.error ||
          error.error?.msg ||
          error.error?.detail));
    if (backendMsg) return throwError(() => new Error(String(backendMsg).trim()));

    if (error.status === 0)
      errorMessage = 'Não foi possível conectar ao servidor.';
    else if (error.status === 400) errorMessage = 'Dados inválidos.';
    else if (error.status === 401)
      errorMessage = 'Não autorizado. Faça login novamente.';
    else if (error.status === 403)
      errorMessage = 'Operação não permitida para seu perfil.';
    else if (error.status === 404) errorMessage = 'Registro não encontrado.';
    else if (error.status === 409) errorMessage = 'Conflito ao salvar.';
    else if (error.status === 500) errorMessage = 'Erro interno do servidor.';
    return throwError(() => new Error(errorMessage));
  }

  private toUTC(date: Date): Date {
    return new Date(Date.UTC(
      date.getFullYear(),
      date.getMonth(),
      date.getDate(),
      date.getHours(),
      date.getMinutes(),
      date.getSeconds()
    ));
  }

  private getDataBrasilia(): Date {
    const agora = new Date();
    const offsetBrasilia = -3 * 60;
    const offsetLocal = agora.getTimezoneOffset();
    const diferenca = offsetBrasilia - offsetLocal;
    
    return new Date(agora.getTime() + diferenca * 60000);
  }

  private formatarDataBrasilia(data: Date): string {
    const offset = data.getTimezoneOffset();
    const brasiliaOffset = 180;
    const correctedDate = new Date(data.getTime() + (offset - brasiliaOffset) * 60000);
    return correctedDate.toISOString().slice(0, 10);
  }

  private parseDataBrasilia(dataStr: string): Date {
    if (!dataStr) return new Date();
    
    if (/^\d{4}-\d{2}-\d{2}$/.test(dataStr)) {
      const [year, month, day] = dataStr.split('-').map(Number);
      return new Date(year, month - 1, day, 12, 0, 0);
    }
    
    return new Date(dataStr);
  }

  listar(params?: {
    tipo?: TipoLancamento | 'TODOS';
    origem?: OrigemLancamento | 'TODAS';
    inicio?: string;
    fim?: string;
  }): Observable<Lancamento[]> {
    let hp = new HttpParams();
    
    if (params?.tipo && params.tipo !== 'TODOS') hp = hp.set('tipo', params.tipo);
    if (params?.origem && params.origem !== 'TODAS') hp = hp.set('origem', params.origem);
    
    if (params?.inicio) {
      const dataInicio = this.parseDataBrasilia(params.inicio);
      const inicioFormatado = this.formatarDataBrasilia(dataInicio);
      hp = hp.set('inicio', inicioFormatado);
    }
    
    if (params?.fim) {
      const dataFim = this.parseDataBrasilia(params.fim);
      const fimFormatado = this.formatarDataBrasilia(dataFim);
      hp = hp.set('fim', fimFormatado);
    }

    return this.http
      .get<Lancamento[]>(this.API_LANC, this.authOptions({ params: hp }))
      .pipe(catchError(this.handleError.bind(this)));
  }

  criar(body: CriarLancamentoPayload): Observable<Lancamento> {
    const valorNumerico = typeof body.valor === 'string' 
      ? Number(body.valor.replace(/[^\d,]/g, '').replace(',', '.'))
      : body.valor;

    const payload = {
      tipo: body.tipo,
      origem: body.origem,
      valor: valorNumerico,
      formaPagamento: body.formaPagamento,
      descricao: body.descricao ?? '',
      referenciaId: body.referenciaId,
    };

    return this.http
      .post<Lancamento>(this.API_LANC, payload, this.authOptions())
      .pipe(catchError(this.handleError.bind(this)));
  }

  atualizar(id: number, body: AtualizarLancamentoPayload): Observable<Lancamento> {
    const valorNumerico = body.valor !== undefined
      ? (typeof body.valor === 'string' 
          ? Number(body.valor.replace(/[^\d,]/g, '').replace(',', '.'))
          : body.valor)
      : undefined;

    const payload: any = {};
    
    if (valorNumerico !== undefined) payload.valor = valorNumerico;
    if (body.formaPagamento !== undefined) payload.formaPagamento = body.formaPagamento;
    if (body.descricao !== undefined) payload.descricao = body.descricao;

    return this.http
      .put<Lancamento>(`${this.API_LANC}/${id}`, payload, this.authOptions())
      .pipe(catchError(this.handleError.bind(this)));
  }

  cancelar(id: number, motivo?: string): Observable<void> {
    const params = motivo ? new HttpParams().set('motivo', motivo) : undefined;
    
    return this.http
      .delete<void>(`${this.API_LANC}/${id}`, this.authOptions({ params }))
      .pipe(catchError(this.handleError.bind(this)));
  }

  debugFusosHorarios(): void {
    const agora = new Date();
    const brasilia = this.getDataBrasilia();
  }
}