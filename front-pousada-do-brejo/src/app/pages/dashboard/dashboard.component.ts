import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';

import {
  DashboardService,
  DashboardOverview,
  DashboardPeriodo,
  Movimento,
  SerieFinanceiroDia,
  SerieOcupacaoDia
} from './dashboard.service';

import {
  NgApexchartsModule,
  ApexAxisChartSeries,
  ApexChart,
  ApexDataLabels,
  ApexGrid,
  ApexLegend,
  ApexStroke,
  ApexXAxis,
  ApexYAxis,
  ApexFill,
  ApexMarkers,
  ApexTooltip,
  ApexPlotOptions,
  ApexNonAxisChartSeries
} from 'ng-apexcharts';

export type AxisChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  dataLabels: ApexDataLabels;
  grid: ApexGrid;
  stroke: ApexStroke;
  xaxis: ApexXAxis;
  yaxis: ApexYAxis | ApexYAxis[];
  legend: ApexLegend;
  fill: ApexFill;
  markers: ApexMarkers;
  tooltip: ApexTooltip;
  colors: string[];
  plotOptions?: ApexPlotOptions;
};

export type RadialChartOptions = {
  series: ApexNonAxisChartSeries;
  chart: ApexChart;
  plotOptions: ApexPlotOptions;
  labels: string[];
  fill: ApexFill;
  stroke: ApexStroke;
  colors: string[];
  legend: ApexLegend;
  grid: ApexGrid;
};

type FiltroMov = 'ALL' | 'IN' | 'OUT';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, NgApexchartsModule],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit, OnDestroy {
  loading = true;
  error = '';
  data: DashboardOverview | null = null;

  // filtro geral do dashboard
  periodo: DashboardPeriodo = 'ULTIMOS_7_DIAS';

  chartFinanceiro: AxisChartOptions | null = null;
  chartOcupacao: AxisChartOptions | null = null;
  chartTaxaOcupacao: RadialChartOptions | null = null;

  showMovModal = false;
  filtroMov: FiltroMov = 'ALL';

  private previousBodyOverflow: string | null = null;

  pageMov = 1;
  pageSizeMov = 8;

  showSaldoDetalhado = false;
  showRecebDetalhado = false;

  showPeriodoBox = false;

  private themeObserver?: MutationObserver;

  constructor(private dashSvc: DashboardService) {}

  ngOnInit(): void {
    this.loading = true;
    this.carregar(this.periodo);
    this.observarTema();
  }

  ngOnDestroy(): void {
    if (this.themeObserver) this.themeObserver.disconnect();
    try {
      document.body.style.overflow = this.previousBodyOverflow ?? '';
    } catch {
      // noop
    }
  }

  // =================== CARREGAMENTO ===================
  private carregar(periodo: DashboardPeriodo): void {
    this.loading = true;
    this.error = '';
    this.periodo = periodo;

    this.dashSvc.overview(periodo).subscribe({
      next: (res: DashboardOverview) => {
        this.data = res;
        this.loading = false;
        this.montarGraficos();
      },
      error: () => {
        this.error = 'Falha ao carregar o dashboard.';
        this.loading = false;
      }
    });
  }

  setPeriodo(p: DashboardPeriodo): void {
    if (p === this.periodo) {
      this.showPeriodoBox = false;
      return;
    }
    this.showPeriodoBox = false;
    this.carregar(p);
  }

  togglePeriodoBox(): void {
    this.showPeriodoBox = !this.showPeriodoBox;
  }

  // ================= TEMA =====================
  private observarTema(): void {
    const body = document.body;
    this.themeObserver = new MutationObserver(() => this.montarGraficos());
    this.themeObserver.observe(body, {
      attributes: true,
      attributeFilter: ['class']
    });
  }

  private themeColors() {
    const styles = getComputedStyle(document.documentElement);
    const dark = document.body.classList.contains('dark-theme');

    const baseText = (styles.getPropertyValue('--text-color') || '#111827').trim();
    const baseDim = (styles.getPropertyValue('--text-dim') || '#6b7280').trim();
    const border = (styles.getPropertyValue('--border-color') || '#e5e7eb').trim();
    const card = (styles.getPropertyValue('--bg-card') || '#ffffff').trim();
    const brand = (styles.getPropertyValue('--accent-lime') || '#a3e635').trim();

    const chartText = dark ? '#f9fafb' : baseText;
    const chartDim = dark ? '#e5e7eb' : baseDim;

    return { text: baseText, dim: baseDim, border, card, brand, chartText, chartDim, dark };
  }

  // ================= DATETIME HELPERS (CORRIGE LABELS REPETIDAS) =================
  private getPeriodoYearFallback(): number {
    const fim: any = (this.data as any)?.periodoFim;
    const iso =
      fim instanceof Date ? fim.toISOString().slice(0, 10) : String(fim ?? '');
    const y = Number(iso.slice(0, 4));
    return Number.isFinite(y) && y > 2000 ? y : new Date().getFullYear();
  }

  private toTimestampFromSerieItem(item: any): number {
    // Preferir campo "data" (LocalDate do back -> 'yyyy-MM-dd')
    if (item?.data) {
      const dt = new Date(String(item.data).slice(0, 10));
      const t = dt.getTime();
      if (!Number.isNaN(t)) return t;
    }

    // fallback: labelDia tipo "dd/MM" ou "dd/MM/yy"
    const label = String(item?.labelDia ?? '').trim();
    if (label.includes('/')) {
      const parts = label.split('/');
      const d = Number(parts[0]);
      const m = Number(parts[1]);
      const y =
        parts.length >= 3
          ? Number(parts[2].length === 2 ? `20${parts[2]}` : parts[2])
          : this.getPeriodoYearFallback();

      const dt = new Date(y, (m || 1) - 1, d || 1);
      const t = dt.getTime();
      if (!Number.isNaN(t)) return t;
    }

    return new Date().getTime();
  }

  private xLabelFormat(): string {
    // HOJE/7 dias: dd/MM
    if (this.periodo === 'HOJE' || this.periodo === 'ULTIMOS_7_DIAS') return 'dd/MM';
    // mês/ano: o Apex vai espaçar automaticamente
    return 'dd/MM';
  }

  // ================= GRAFICOS =====================
  private montarGraficos(): void {
    if (!this.data) return;

    const theme = this.themeColors();

    this.chartFinanceiro = this.buildFinanceiroChart(this.data.serieFinanceiro, theme);
    this.chartOcupacao = this.buildOcupacaoChart(this.data.serieOcupacao, theme);

    // pode vir como data.taxaOcupacao OU data.taxaOcupacaoPercentual dependendo do seu DTO/mapeamento
    const taxa =
      (this.data as any)?.taxaOcupacao ??
      (this.data as any)?.taxaOcupacaoPercentual ??
      0;

    this.chartTaxaOcupacao = this.buildTaxaOcupacaoChart(Number(taxa ?? 0), theme);
  }

  private calcularMaxFinanceiro(entradas: number[], saidas: number[]): number {
    const valores = [...entradas, ...saidas].map((v) => Math.abs(v ?? 0));
    const max = Math.max(...valores, 0);
    if (max === 0) return 1000;

    const alvo = max * 1.25;
    const potencia = Math.pow(10, Math.floor(Math.log10(alvo)));
    const step = potencia / 2;
    return Math.ceil(alvo / step) * step;
  }

  private calcularMaxOcupacao(serie: SerieOcupacaoDia[]): number {
    const maxValor = Math.max(...serie.map((d: any) => Math.max(d.hospedagens ?? 0, d.reservas ?? 0)), 0);
    if (maxValor <= 30) return 30;
    if (maxValor <= 50) return 50;
    if (maxValor <= 100) return 100;
    return Math.ceil(maxValor / 20) * 20;
  }

  private buildFinanceiroChart(
    serie: SerieFinanceiroDia[],
    theme: ReturnType<DashboardComponent['themeColors']>
  ): AxisChartOptions {
    const entradasPts = (serie || []).map((d: any) => ({
      x: this.toTimestampFromSerieItem(d),
      y: Number(d.entradas ?? 0)
    }));

    const saidasPts = (serie || []).map((d: any) => ({
      x: this.toTimestampFromSerieItem(d),
      y: Number(d.saidas ?? 0)
    }));

    const maxY = this.calcularMaxFinanceiro(
      entradasPts.map((p) => p.y),
      saidasPts.map((p) => p.y)
    );

    return {
      series: [
        { name: 'Entradas', data: entradasPts as any },
        { name: 'Saídas', data: saidasPts as any }
      ],
      chart: {
        type: 'area',
        height: 260,
        toolbar: { show: false },
        foreColor: theme.chartDim,
        background: 'transparent'
      },
      colors: [theme.brand, '#fb7185'],
      dataLabels: { enabled: false },
      stroke: { curve: 'smooth', width: 3 },
      fill: {
        type: 'gradient',
        gradient: { shadeIntensity: 0.5, opacityFrom: 0.25, opacityTo: 0, stops: [0, 60, 100] }
      },
      grid: { borderColor: theme.border, strokeDashArray: 4 },
      xaxis: {
        type: 'datetime',
        labels: {
          datetimeUTC: false,
          style: { colors: [theme.chartDim], fontSize: '11px' },
          format: this.xLabelFormat()
        }
      },
      yaxis: {
        min: 0,
        max: maxY,
        tickAmount: 5,
        labels: {
          style: { colors: [theme.chartDim], fontSize: '11px' },
          formatter: (value: number) =>
            value.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
        }
      },
      markers: { size: 3, strokeWidth: 1, strokeColors: theme.card },
      legend: {
        position: 'top',
        horizontalAlign: 'left',
        labels: { colors: theme.chartText },
        markers: { radius: 12 }
      },
      tooltip: {
        theme: theme.dark ? 'dark' : 'light',
        x: { format: 'dd/MM/yyyy' },
        y: { formatter: (v: number) => (v ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }) }
      }
    };
  }

  private buildOcupacaoChart(
    serie: SerieOcupacaoDia[],
    theme: ReturnType<DashboardComponent['themeColors']>
  ): AxisChartOptions {
    const hospedagensPts = (serie || []).map((d: any) => ({
      x: this.toTimestampFromSerieItem(d),
      y: Number(d.hospedagens ?? 0)
    }));

    const reservasPts = (serie || []).map((d: any) => ({
      x: this.toTimestampFromSerieItem(d),
      y: Number(d.reservas ?? 0)
    }));

    const maxY = this.calcularMaxOcupacao(serie || []);

    return {
      series: [
        { name: 'Hospedagens', data: hospedagensPts as any },
        { name: 'Reservas', data: reservasPts as any }
      ],
      chart: {
        type: 'bar',
        stacked: true,
        height: 260,
        toolbar: { show: false },
        foreColor: theme.chartDim,
        background: 'transparent'
      },
      colors: ['#22c55e', '#38bdf8'],
      plotOptions: { bar: { columnWidth: '45%', borderRadius: 6 } },
      dataLabels: { enabled: false },
      fill: { opacity: 0.9 },
      grid: { borderColor: theme.border, strokeDashArray: 4 },
      xaxis: {
        type: 'datetime',
        labels: {
          datetimeUTC: false,
          style: { colors: [theme.chartDim], fontSize: '11px' },
          format: this.xLabelFormat()
        }
      },
      yaxis: {
        min: 0,
        max: maxY,
        tickAmount: 5,
        labels: { style: { colors: [theme.chartDim], fontSize: '11px' } }
      },
      stroke: { show: true, width: 0, colors: ['transparent'] },
      markers: { size: 0 },
      legend: {
        position: 'top',
        horizontalAlign: 'left',
        labels: { colors: theme.chartText },
        markers: { radius: 12 }
      },
      tooltip: {
        theme: theme.dark ? 'dark' : 'light',
        x: { format: 'dd/MM/yyyy' },
        y: { formatter: (val: number) => String(val ?? 0) }
      }
    };
  }

  private buildTaxaOcupacaoChart(
    percentual: number,
    theme: ReturnType<DashboardComponent['themeColors']>
  ): RadialChartOptions {
    return {
      series: [Math.round(Number(percentual ?? 0)) || 0],
      chart: { type: 'radialBar', height: 200, sparkline: { enabled: true } },
      colors: [theme.brand],
      plotOptions: {
        radialBar: {
          hollow: { size: '65%' },
          track: { background: theme.border, opacity: 0.5 },
          dataLabels: {
            show: true,
            name: { show: true, fontSize: '11px', color: theme.chartDim, offsetY: 22 },
            value: {
              show: true,
              fontSize: '24px',
              fontWeight: 'bold',
              color: theme.chartText,
              offsetY: -8,
              formatter: (val: number) => `${val}%`
            }
          }
        }
      },
      labels: ['Ocupação'],
      fill: { opacity: 1 },
      stroke: { lineCap: 'round' },
      legend: { show: false },
      grid: { padding: { top: 10, bottom: 10 } }
    };
  }

  // ================= HELPERS =====================
  brMoney(n: number | null | undefined): string {
    return Number(n ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  brDateTime(iso: string): string {
    if (!iso) return '-';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '-';
    return d.toLocaleString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  isOut(m: Movimento): boolean {
    return (m as any)?.tipo === 'OUT' || (m as any)?.valor < 0;
  }

  abs(n: number): number {
    return Math.abs(n ?? 0);
  }

  labelTipo(m: Movimento): string {
    const t = String((m as any)?.tipo || '').toUpperCase();
    if (t === 'IN') return 'Entrada';
    if (t === 'OUT') return 'Saída';
    const v = (m as any)?.valor;
    if (typeof v === 'number') return v >= 0 ? 'Entrada' : 'Saída';
    return 'Mov.';
  }

  // ================== PERÍODO UI ==================
  periodoLabel(p: DashboardPeriodo): string {
    if (p === 'ESTE_ANO') return 'Este ano';
    if (p === 'ESTE_MES') return 'Este mês';
    if (p === 'HOJE') return 'Hoje';
    return 'Últimos 7 dias';
  }

  periodoHint(): string {
    const ini: any = (this.data as any)?.periodoInicio;
    const fim: any = (this.data as any)?.periodoFim;
    if (!ini || !fim) return '';

    const toISO = (v: string | Date) => {
      if (v instanceof Date) return v.toISOString().slice(0, 10);
      return String(v).slice(0, 10);
    };

    const toDM = (iso: string) => {
      // yyyy-MM-dd -> dd/MM
      const [y, m, d] = iso.split('-');
      if (!y || !m || !d) return iso;
      return `${d}/${m}`;
    };

    return `${toDM(toISO(ini))} → ${toDM(toISO(fim))}`;
  }

  // ================= MOVIMENTAÇÕES =====================
  get recentesTop3(): Movimento[] {
    return ((this.data as any)?.recentes ?? []).slice(0, 3);
  }

  get saldoAtual(): number {
    return Number((this.data as any)?.kpis?.saldoAtual ?? 0);
  }

  get saldoPrefeitura(): number {
    return Number((this.data as any)?.kpis?.saldoPrefeitura ?? 0);
  }

  get saldoNaoPrefeitura(): number {
    return this.saldoAtual - this.saldoPrefeitura;
  }

  // Modal / paginação
  abrirMovModal(): void {
    this.showMovModal = true;
    this.filtroMov = 'ALL';
    this.pageMov = 1;

    try {
      this.previousBodyOverflow = document.body.style.overflow || null;
      document.body.style.overflow = 'hidden';
    } catch {
      // noop
    }
  }

  fecharMovModal(): void {
    this.showMovModal = false;
    try {
      document.body.style.overflow = this.previousBodyOverflow ?? '';
      this.previousBodyOverflow = null;
    } catch {
      // noop
    }
  }

  setFiltroMov(tipo: FiltroMov): void {
    this.filtroMov = tipo;
    this.pageMov = 1;
  }

  private filteredMovimentosSemPagina(): Movimento[] {
    const all: Movimento[] = (this.data as any)?.recentes ?? [];
    if (this.filtroMov === 'IN') return all.filter((m) => !this.isOut(m));
    if (this.filtroMov === 'OUT') return all.filter((m) => this.isOut(m));
    return all;
  }

  get totalMovFiltrados(): number {
    return this.filteredMovimentosSemPagina().length;
  }

  get totalPagesMov(): number {
    const total = this.totalMovFiltrados;
    if (!total) return 1;
    return Math.ceil(total / this.pageSizeMov);
  }

  get movFiltrados(): Movimento[] {
    const filtered = this.filteredMovimentosSemPagina();
    if (!filtered.length) {
      this.pageMov = 1;
      return [];
    }

    const maxPage = Math.max(1, Math.ceil(filtered.length / this.pageSizeMov));
    if (this.pageMov > maxPage) this.pageMov = maxPage;

    const start = (this.pageMov - 1) * this.pageSizeMov;
    return filtered.slice(start, start + this.pageSizeMov);
  }

  get canPrevMov(): boolean {
    return this.pageMov > 1 && this.totalMovFiltrados > 0;
  }

  get canNextMov(): boolean {
    return this.pageMov < this.totalPagesMov && this.totalMovFiltrados > 0;
  }

  prevMovPage(): void {
    if (this.canPrevMov) this.pageMov--;
  }

  nextMovPage(): void {
    if (this.canNextMov) this.pageMov++;
  }

  // Toggles dos cards
  toggleSaldoDetalhe(): void {
    this.showSaldoDetalhado = !this.showSaldoDetalhado;
  }

  toggleRecebDetalhe(): void {
    this.showRecebDetalhado = !this.showRecebDetalhado;
  }
}
