import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

// ======== INTERFACES ========
export interface PeriodoFilter { 
  dataInicio: string; 
  dataFim: string; 
}

export interface FinanceiroFilter extends PeriodoFilter {
  tipo?: 'TODAS' | 'ENTRADAS' | 'SAIDAS';
}

export interface HospedagensFilter extends PeriodoFilter {
  status: 'TODAS' | 'ATIVAS' | 'INATIVAS';
  tipo: 'TODOS' | 'Individual' | 'Duplo' | 'Triplo' | string;
}

export interface QuartosFilter extends PeriodoFilter {
  status: 'TODOS' | 'Disponível' | 'Ocupado' | 'Manutenção' | string;
  tipo: 'TODOS' | 'Individual' | 'Duplo' | 'Triplo' | string;
}

export interface ReservasFilter extends PeriodoFilter {
  status: 'TODAS' | 'PENDENTE' | 'CONFIRMADA' | 'CANCELADA' | string;
}

// ======== SERVICE ========
@Injectable({ providedIn: 'root' })
export class ReportsService {

  private api = 'http://localhost:8080/api/admin/reports';

  constructor(private http: HttpClient) {}

  private authOptions() {
    const token = localStorage.getItem('token');
    return token ? { headers: { Authorization: `Bearer ${token}` } } : {};
  }

  // ----------------- CORREÇÕES DE FUSO HORÁRIO -----------------

  /**
   * Obtém a data atual no fuso de Brasília
   */
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
    if (!dataStr) return this.getDataBrasilia();
    
    if (/^\d{4}-\d{2}-\d{2}$/.test(dataStr)) {
      const [year, month, day] = dataStr.split('-').map(Number);
      return new Date(year, month - 1, day, 12, 0, 0);
    }
    
    return new Date(dataStr);
  }

  private corrigirFiltroDatas<T extends PeriodoFilter>(filtro: T): T {
    const dataInicio = this.parseDataBrasilia(filtro.dataInicio);
    const dataFim = this.parseDataBrasilia(filtro.dataFim);
    
    return {
      ...filtro,
      dataInicio: this.formatarDataBrasilia(dataInicio),
      dataFim: this.formatarDataBrasilia(dataFim)
    };
  }

  // ======== GERAL ========
  exportGeral(f: PeriodoFilter): Observable<HttpResponse<Blob>> {
    const filtroCorrigido = this.corrigirFiltroDatas(f);
    
    return this.http.post(`${this.api}/geral/export`, filtroCorrigido, {
      ...this.authOptions(),
      observe: 'response',
      responseType: 'blob'
    });
  }

  // ======== FINANCEIRO ========
  exportFinanceiro(f: FinanceiroFilter): Observable<HttpResponse<Blob>> {
    const filtroCorrigido = this.corrigirFiltroDatas(f);
    
    return this.http.post(`${this.api}/financeiro/export`, filtroCorrigido, {
      ...this.authOptions(),
      observe: 'response',
      responseType: 'blob'
    });
  }

  // ======== HOSPEDAGENS ========
  exportHospedagens(f: HospedagensFilter): Observable<HttpResponse<Blob>> {
    const filtroCorrigido = this.corrigirFiltroDatas(f);
    
    return this.http.post(`${this.api}/hospedagens/export`, filtroCorrigido, {
      ...this.authOptions(),
      observe: 'response',
      responseType: 'blob'
    });
  }

  // ======== QUARTOS ========
  exportQuartos(f: QuartosFilter): Observable<HttpResponse<Blob>> {
    const filtroCorrigido = this.corrigirFiltroDatas(f);
    
    return this.http.post(`${this.api}/quartos/export`, filtroCorrigido, {
      ...this.authOptions(),
      observe: 'response',
      responseType: 'blob'
    });
  }

  // ======== RESERVAS ========
  exportReservas(f: ReservasFilter): Observable<HttpResponse<Blob>> {
    const filtroCorrigido = this.corrigirFiltroDatas(f);
    
    return this.http.post(`${this.api}/reservas/export`, filtroCorrigido, {
      ...this.authOptions(),
      observe: 'response',
      responseType: 'blob'
    });
  }

  // ======== DOWNLOAD PDF ========
  downloadFromResponse(res: HttpResponse<Blob>, fallbackName: string) {
    const dispo = res.headers.get('content-disposition') || '';
    const utf = /filename\*=UTF-8''([^;]+)/i.exec(dispo);
    const basic = /filename="?([^"]+)"?/i.exec(dispo);
    const raw = (utf?.[1] || basic?.[1] || fallbackName);
    const filename = decodeURIComponent(raw);

    const blob = new Blob([res.body!], { type: 'application/pdf' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; 
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }
}