import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { catchError, map, Observable, of } from 'rxjs';

export type DashboardPeriodo = 'ESTE_ANO' | 'ESTE_MES' | 'ULTIMOS_7_DIAS' | 'HOJE';
export type MovimentoTipo = 'IN' | 'OUT';

export interface Movimento {
  dataISO: string;
  descricao: string;
  origem: string;
  valor: number;
  tipo: MovimentoTipo;
}

export interface SerieFinanceiroDia {
  dataISO: string;
  labelDia: string;
  entradas: number;
  saidas: number;
}

export interface SerieOcupacaoDia {
  dataISO: string;
  labelDia: string;
  hospedagens: number;
  reservas: number;
}

export interface ResumoSaldo {
  recebimentos: number;
  saidas: number;

  recebimentosPrefeitura: number;
  recebimentosComuns?: number;
  recebimentosCorporativos?: number;
}

export interface Kpis {
  hospedagensAtivas: number;
  quartosAtivos: number;
  reservasPendentes: number;
  usuariosAtivos: number;

  saldoAtual: number;
  saldoPrefeitura: number;
  saldoDemaisClientes?: number;
}

export interface AcoesDoDia {
  checkinsPendentes: number;
  checkoutsPendentes: number;
}

export interface DashboardOverview {
  periodo: DashboardPeriodo;
  periodoInicio: string; // yyyy-MM-dd
  periodoFim: string;    // yyyy-MM-dd

  kpis: Kpis;
  recentes: Movimento[]; // 90d fixo
  resumo: ResumoSaldo;

  serieFinanceiro: SerieFinanceiroDia[];
  serieOcupacao: SerieOcupacaoDia[];

  acoes: AcoesDoDia;
  taxaOcupacao: number;
}

// ================= BACKEND DTO (DashboardResumoDTO) =================
interface DashboardApiResponse {
  periodo: string;
  periodoInicio: string;
  periodoFim: string;

  hospedagensAtivas: number;
  quartosAtivos: number;
  reservasPendentes: number;
  usuariosAtivos: number;

  saldoAtual: number;
  saldoPrefeitura?: number;
  saldoDemaisClientes?: number;

  recebimentosPrefeitura?: number;
  recebimentosCorporativos?: number;
  recebimentosComuns?: number;

  ultimasMovimentacoes: Array<{
    id: number;
    dataHora: string;
    origem: string;
    tipo: 'ENTRADA' | 'SAIDA';
    valor: number;
    descricao: string;
  }>;

  serieFinanceiro: Array<{
    data: string; // yyyy-MM-dd
    totalEntradas: number;
    totalSaidas: number;
  }>;

  serieOcupacao: Array<{
    data: string; // yyyy-MM-dd
    qtdHospedagens: number;
    qtdReservas: number;
  }>;

  checkinsPendentesHoje?: number;
  checkoutsPendentesHoje?: number;
  taxaOcupacaoPercentual?: number;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly API = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  private labelDia(iso: string): string {
    // iso = yyyy-MM-dd
    if (!iso) return '';
    const [y, m, d] = iso.split('-').map(Number);
    const dt = new Date(y, (m ?? 1) - 1, d ?? 1, 12, 0, 0);
    return dt.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  }

  overview(periodo: DashboardPeriodo = 'ULTIMOS_7_DIAS'): Observable<DashboardOverview> {
    const params = new HttpParams().set('periodo', periodo);

    return this.http
      .get<DashboardApiResponse>(`${this.API}/dashboard/resumo`, { params })
      .pipe(
        map((res) => {
          // KPIs
          const kpis: Kpis = {
            hospedagensAtivas: res.hospedagensAtivas ?? 0,
            quartosAtivos: res.quartosAtivos ?? 0,
            reservasPendentes: res.reservasPendentes ?? 0,
            usuariosAtivos: res.usuariosAtivos ?? 0,
            saldoAtual: res.saldoAtual ?? 0,
            saldoPrefeitura: res.saldoPrefeitura ?? 0,
            saldoDemaisClientes: res.saldoDemaisClientes ?? undefined
          };

          // Movimentações (90d fixo)
          const recentes: Movimento[] = (res.ultimasMovimentacoes ?? [])
            .map((m) => ({
              dataISO: m.dataHora,
              descricao: m.descricao,
              origem: m.origem,
              valor: Number(m.valor || 0),
              tipo: (m.tipo === 'SAIDA' ? 'OUT' : 'IN') as MovimentoTipo
            }))
            .sort((a, b) => new Date(b.dataISO).getTime() - new Date(a.dataISO).getTime());

          // Resumo: agora TOTAL vem do PERÍODO (não das movimentações)
          const recebPref = res.recebimentosPrefeitura ?? 0;
          const recebComuns = res.recebimentosComuns ?? 0;
          const recebCorp = res.recebimentosCorporativos ?? 0;

          const recebimentos = recebPref + recebComuns + recebCorp;

          // Saídas do período: dá pra estimar pela série (mais correto seria vir do back),
          // mas como você não pediu alterar backend agora, a gente soma da série.
          const saidas = (res.serieFinanceiro ?? []).reduce((acc, d) => acc + (d.totalSaidas ?? 0), 0);

          const resumo: ResumoSaldo = {
            recebimentos,
            saidas,
            recebimentosPrefeitura: recebPref,
            recebimentosComuns: recebComuns,
            recebimentosCorporativos: recebCorp
          };

          // Série financeira
          const serieFinanceiro: SerieFinanceiroDia[] = (res.serieFinanceiro ?? []).map((d) => ({
            dataISO: d.data,
            labelDia: this.labelDia(d.data),
            entradas: d.totalEntradas ?? 0,
            saidas: d.totalSaidas ?? 0
          }));

          // Série ocupação
          const serieOcupacao: SerieOcupacaoDia[] = (res.serieOcupacao ?? []).map((d) => ({
            dataISO: d.data,
            labelDia: this.labelDia(d.data),
            hospedagens: d.qtdHospedagens ?? 0,
            reservas: d.qtdReservas ?? 0
          }));

          const acoes: AcoesDoDia = {
            checkinsPendentes: res.checkinsPendentesHoje ?? 0,
            checkoutsPendentes: res.checkoutsPendentesHoje ?? 0
          };

          return {
            periodo: (res.periodo as DashboardPeriodo) || periodo,
            periodoInicio: res.periodoInicio,
            periodoFim: res.periodoFim,
            kpis,
            recentes,
            resumo,
            serieFinanceiro,
            serieOcupacao,
            acoes,
            taxaOcupacao: res.taxaOcupacaoPercentual ?? 0
          };
        }),
        catchError(() =>
          of({
            periodo,
            periodoInicio: '',
            periodoFim: '',
            kpis: {
              hospedagensAtivas: 0,
              quartosAtivos: 0,
              reservasPendentes: 0,
              usuariosAtivos: 0,
              saldoAtual: 0,
              saldoPrefeitura: 0
            },
            recentes: [],
            resumo: {
              recebimentos: 0,
              saidas: 0,
              recebimentosPrefeitura: 0,
              recebimentosComuns: 0,
              recebimentosCorporativos: 0
            },
            serieFinanceiro: [],
            serieOcupacao: [],
            acoes: { checkinsPendentes: 0, checkoutsPendentes: 0 },
            taxaOcupacao: 0
          } as DashboardOverview)
        )
      );
  }
}
