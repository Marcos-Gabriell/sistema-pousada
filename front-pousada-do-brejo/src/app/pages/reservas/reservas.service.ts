import { Injectable } from '@angular/core';
import {
  HttpClient,
  HttpParams,
  HttpErrorResponse,
  HttpHeaders,
} from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';

export type FormaPagamento = 'DINHEIRO' | 'DEBITO' | 'CREDITO' | 'PIX';
export type TipoCliente = 'COMUM' | 'PREFEITURA' | 'CORPORATIVO';

export interface Quarto {
  id: number;
  numero: string;
  tipo?: string;
  valorDiaria?: number;
  status?: string;
}

export interface Reserva {
  id: number;
  codigo: string | null;
  nome: string;
  telefone: string;
  cpf: string;
  email: string;
  tipoCliente: TipoCliente | string;
  numeroQuarto: string;
  dataEntrada: string;
  dataSaida: string;
  numeroDiarias: number;
  status: 'PENDENTE' | 'CONFIRMADA' | 'CANCELADA' | 'FINALIZADA';
  observacoes: string;
  observacoesCheckin?: string | null;
  dataReserva?: string;
  valorDiaria?: number;
  valorTotal?: number;
  formaPagamento?: FormaPagamento | string;
  motivoCancelamento?: string;
  autorId?: number;
  autorNome?: string;
  confirmadorId?: number;
  confirmadorNome?: string;
  confirmadoEm?: string;
  canceladorId?: number;
  canceladorNome?: string;
  canceladoEm?: string;
}

export type CriarReservaPayload = {
  nome: string;
  numeroQuarto: string;
  dataEntrada: string;
  numeroDiarias: number;
  observacoes: string;
  telefone: string;
  cpf: string;
  email: string;
  tipoCliente: TipoCliente | string;
  valorDiaria: number;
  formaPagamento: FormaPagamento;
};

export type AtualizarReservaPayload = {
  id: number;
  nome: string;
  numeroQuarto: string;
  dataEntrada: string;
  numeroDiarias: number;
  observacoes: string;
  telefone: string;
  cpf: string;
  email: string;
  tipoCliente: TipoCliente | string;
  valorDiaria: number;
  formaPagamento: FormaPagamento;
};

export type ConfirmarReservaPayload = {
  tipoCliente?: TipoCliente | string;
  observacoesCheckin?: string;
  cpf?: string;
  email?: string;
};

@Injectable({ providedIn: 'root' })
export class ReservaService {
  private readonly API = 'http://localhost:8080/api';
  private readonly API_RESERVAS = `${this.API}/reservas`;

  constructor(private http: HttpClient) {}

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
    const correctedDate = new Date(
      data.getTime() + (offset - brasiliaOffset) * 60000
    );
    return correctedDate.toISOString().slice(0, 10);
  }

  private parseDataBrasilia(dataStr: string): Date {
    if (!dataStr) return this.getDataBrasilia();

    if (/^\d{4}-\d{2}-\d{2}$/.test(dataStr)) {
      const [year, month, day] = dataStr.split('-').map(Number);
      return new Date(year, month - 1, day, 12, 0, 0);
    }

    return new Date(dataStr);
  }

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

  private toYMD(dateLike: string): string {
    if (!dateLike) return '';

    try {
      if (
        typeof dateLike === 'string' &&
        /^\d{4}-\d{2}-\d{2}$/.test(dateLike)
      ) {
        return dateLike;
      }

      const date = this.parseDataBrasilia(dateLike);
      if (isNaN(date.getTime())) {
        console.error('Data inválida:', dateLike);
        return '';
      }

      return this.formatarDataBrasilia(date);
    } catch (error) {
      console.error('Erro ao formatar data:', dateLike, error);
      return '';
    }
  }

  private handleError(error: HttpErrorResponse) {
    let errorMessage = 'Erro desconhecido';

    if (error.error instanceof ErrorEvent) {
      errorMessage = `Erro: ${error.error.message}`;
      return throwError(() => new Error(errorMessage));
    }

    errorMessage = `Erro ${error.status}: ${error.message}`;

    const backendCandidate = (() => {
      const p = error.error;
      if (!p) return null;
      if (typeof p === 'string') return p;
      if (typeof p === 'object') {
        return p.message || p.error || p.msg || p.detail || null;
      }
      return null;
    })();

    if (backendCandidate && String(backendCandidate).trim()) {
      const rawMsg = String(backendCandidate).toString();
      const lower = rawMsg.toLowerCase();
      const idx = lower.indexOf('cpf');
      if (idx >= 0) {
        let extracted = rawMsg.substring(idx);
        extracted = extracted.replace(/^(cpf[\s:\-–—]*)/i, '').trim();
        extracted = extracted.replace(/^[\:\-\s]+/, '');
        if (!/^cpf/i.test(extracted)) extracted = 'CPF ' + extracted;
        extracted = extracted.replace(/[\.\s]+$/, '');
        errorMessage = extracted;
      } else {
        errorMessage = rawMsg;
      }

      return throwError(() => new Error(errorMessage));
    }

    if (error.status === 500) {
      errorMessage = 'Erro interno do servidor. Tente novamente mais tarde.';
    } else if (error.status === 401) {
      errorMessage = 'Não autorizado. Faça login novamente.';
    } else if (error.status === 404) {
      errorMessage = 'Recurso não encontrado.';
    } else if (error.status === 0) {
      errorMessage =
        'Não foi possível conectar ao servidor. Verifique sua conexão.';
    } else if (error.status === 409) {
      errorMessage = error.error?.message || 'Conflito de dados.';
    } else if (error.status === 400) {
      errorMessage = 'Dados inválidos. Verifique os campos.';
    }

    return throwError(() => new Error(errorMessage));
  }

  listar(): Observable<Reserva[]> {
    return this.http
      .get<Reserva[]>(this.API_RESERVAS, this.authOptions())
      .pipe(catchError(this.handleError.bind(this)));
  }

  buscarPorId(id: number): Observable<Reserva> {
    return this.http
      .get<Reserva>(`${this.API_RESERVAS}/${id}`, this.authOptions())
      .pipe(catchError(this.handleError.bind(this)));
  }

  listarPorStatus(status: string): Observable<Reserva[]> {
    return this.http
      .get<Reserva[]>(`${this.API_RESERVAS}/status/${status}`, this.authOptions())
      .pipe(catchError(this.handleError.bind(this)));
  }

  criar(body: CriarReservaPayload): Observable<Reserva> {
    const payload: CriarReservaPayload = {
      ...body,
      dataEntrada: this.toYMD(body.dataEntrada),
      observacoes: body.observacoes ?? '',
      telefone: body.telefone ?? '',
      cpf: body.cpf ?? '',
      email: body.email ?? '',
      tipoCliente: (body.tipoCliente as TipoCliente) ?? 'COMUM',
    };

    return this.http
      .post<Reserva>(this.API_RESERVAS, payload, this.authOptions())
      .pipe(catchError(this.handleError.bind(this)));
  }

  atualizar(id: number, body: AtualizarReservaPayload): Observable<Reserva> {
    const payload: AtualizarReservaPayload = {
      ...body,
      dataEntrada: this.toYMD(body.dataEntrada),
      observacoes: body.observacoes ?? '',
      telefone: body.telefone ?? '',
      cpf: body.cpf ?? '',
      email: body.email ?? '',
      tipoCliente: (body.tipoCliente as TipoCliente) ?? 'COMUM',
    };

    return this.http
      .put<Reserva>(`${this.API_RESERVAS}/${id}`, payload, this.authOptions())
      .pipe(catchError(this.handleError.bind(this)));
  }

  cancelar(id: number, motivo: string = ''): Observable<void> {
    const headers = new HttpHeaders({
      'Content-Type': 'text/plain; charset=utf-8',
    });

    const body = motivo ?? '';

    return this.http
      .post<void>(
        `${this.API_RESERVAS}/${id}/cancelar`,
        body,
        this.authOptions({ headers })
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  confirmar(
    id: number,
    payload: ConfirmarReservaPayload = {}
  ): Observable<Reserva> {
    if (payload.cpf) {
      payload = { ...payload, cpf: payload.cpf.replace(/\D/g, '') };
    }

    return this.http
      .put<Reserva>(
        `${this.API_RESERVAS}/${id}/confirmar`,
        payload,
        this.authOptions()
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  /* --------- Quartos Disponíveis --------- */

  listarQuartosDisponiveisPorPeriodo(
    dataEntrada: string,
    dataSaida: string
  ): Observable<Quarto[]> {
    const d1 = this.toYMD(dataEntrada);
    const d2 = this.toYMD(dataSaida);

    const params = new HttpParams().set('dataEntrada', d1).set('dataSaida', d2);

    return this.http
      .get<Quarto[]>(
        `${this.API_RESERVAS}/quartos/disponiveis`,
        this.authOptions({ params })
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  healthCheck(): Observable<{ status: string }> {
    return this.http
      .get<{ status: string }>(`${this.API}/health`, this.authOptions())
      .pipe(catchError(this.handleError.bind(this)));
  }

  listarPorPeriodo(dataInicio: string, dataFim: string): Observable<Reserva[]> {
    const params = new HttpParams()
      .set('dataInicio', this.toYMD(dataInicio))
      .set('dataFim', this.toYMD(dataFim));

    return this.http
      .get<Reserva[]>(
        `${this.API_RESERVAS}/periodo`,
        this.authOptions({ params })
      )
      .pipe(catchError(this.handleError.bind(this)));
  }

  isValidDate(dateString: string): boolean {
    const regex = /^\d{4}-\d{2}-\d{2}$/;
    if (!regex.test(dateString)) return false;

    const date = new Date(dateString);
    return date instanceof Date && !isNaN(date.getTime());
  }

  formatDateForDisplay(dateString: string): string {
    if (!this.isValidDate(dateString)) return dateString;

    const date = new Date(dateString);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();

    return `${day}/${month}/${year}`;
  }

  calcularDiarias(dataEntrada: string, dataSaida: string): number {
    const entrada = this.parseDataBrasilia(dataEntrada);
    const saida = this.parseDataBrasilia(dataSaida);

    const diffTime = saida.getTime() - entrada.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    return diffDays > 0 ? diffDays : 1;
  }
}
