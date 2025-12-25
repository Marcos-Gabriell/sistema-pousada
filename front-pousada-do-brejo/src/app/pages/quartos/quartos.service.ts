import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Quarto {
  id: number;
  codigo?: number;
  numero: string;
  nome?: string;
  tipo: string;
  status?: string;
  valorDiaria?: number;
  descricao?: string;
  criadoPorNome?: string;
  criadoEm?: string | Date;
}

@Injectable({ providedIn: 'root' })
export class QuartosService {
  private api = 'http://localhost:8080/api/quartos';
  
  constructor(private http: HttpClient) {}
  
  private authOptions() {
    const token = localStorage.getItem('token');
    return token ? { headers: { Authorization: `Bearer ${token}` } } : {};
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

  listar(): Observable<Quarto[]> {
    return this.http.get<Quarto[]>(this.api, this.authOptions());
  }
  
  buscar(filtro: any): Observable<Quarto[]> {
    let params = new HttpParams();
    if (filtro?.termo)  params = params.set('termo', filtro.termo);
    if (filtro?.status) params = params.set('status', filtro.status);
    if (filtro?.tipo)   params = params.set('tipo', filtro.tipo);
    
    return this.http.get<Quarto[]>(`${this.api}/buscar`, { 
      params, 
      ...this.authOptions() 
    });
  }
  
  criar(quarto: Quarto): Observable<Quarto> {
    return this.http.post<Quarto>(this.api, quarto, this.authOptions());
  }
  
  editar(id: number, quarto: Quarto): Observable<Quarto> {
    return this.http.put<Quarto>(`${this.api}/${id}`, quarto, this.authOptions());
  }
  
  liberar(id: number): Observable<Quarto> {
    return this.http.put<Quarto>(`${this.api}/${id}/liberar`, {}, this.authOptions());
  }
  
  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`, this.authOptions());
  }
}