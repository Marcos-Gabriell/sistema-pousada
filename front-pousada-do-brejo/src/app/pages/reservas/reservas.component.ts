import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import {
  ReservaService,
  Quarto,
  Reserva,
  AtualizarReservaPayload,
  CriarReservaPayload,
  ConfirmarReservaPayload,
  TipoCliente,
} from './reservas.service';
import { ToastService } from '../../toast/toast.service';
import { ModalScrollService } from '../../services/modal-scroll.service';
import { jsPDF } from 'jspdf';
import html2canvas from 'html2canvas';

import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

type TipoConfirmacao = 'cancelar' | 'checkin';

@Component({
  selector: 'app-reservas',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDatepickerModule,
    MatInputModule,
    MatFormFieldModule,
    MatNativeDateModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './reservas.component.html',
  styleUrls: ['./reservas.component.css'],
})
export class ReservasComponent implements OnInit {
  @ViewChild('rangePicker') rangePicker: any;

  downloadingComprovante = false;

  reservas: Reserva[] = [];
  carregandoLista = false;

  estaBuscandoReservas = false;

  reservasAll: Reserva[] = [];
  reservasFiltradas: Reserva[] = [];
  pagina = 1;
  tamanho = 5;
  totalPaginas = 1;
  totalElementos = 0;

  filtroBusca = '';
  filtroInicio = '';
  filtroFim = '';
  filtroDataInicio = '';
  filtroDataFim = '';
  filtroRange: any = null;
  filtroStatus = '';

  private searchTimer: any = null;

  showCreate = false;
  isEditMode = false;
  submitting = false;

  estaProcessandoAcao = false;
  reservaEmProcessamentoId: number | string | null = null;

  linhaCarregandoId: number | string | null = null;
  linhaAcao: TipoConfirmacao | null = null;

  executandoConfirmacao = false;

  showDetails = false;
  selecionada: Reserva | null = null;

  showConfirm = false;
  acaoConfirmacao: TipoConfirmacao | null = null;
  reservaParaConfirmar: Reserva | null = null;

  motivoCancelamentoModal: string = '';
  observacaoCancelamentoModal: string = '';

  showCreateSuccess = false;
  reservaCriada: Reserva | null = null;

  quartos: Quarto[] = [];
  carregandoQuartos = false;

  dataInicio: Date | null = null;
  dataFim: Date | null = null;
  dataInicioStr: string = '';
  dataFimStr: string = '';

  periodoRangeModel: { start: Date | null; end: Date | null } = {
    start: null,
    end: null,
  };

  reservaModel = {
    nome: '',
    telefone: '',
    cpf: '',
    email: '',
    tipoCliente: '' as '' | TipoCliente,
    observacoes: '',
    valorDiaria: null as number | null,
    formaPagamento: '' as '' | 'DINHEIRO' | 'DEBITO' | 'CREDITO' | 'PIX',
  };

  emailHintsOpen = false;
  emailActiveIndex = 0;
  emailCandidates: string[] = [];
  private readonly commonDomains = [
    'gmail.com',
    'outlook.com',
    'yahoo.com',
    'aol.com',
    'hotmail.com',
    'icloud.com',
    'live.com',
    'proton.me',
  ];

  quartoNumero: string = '';
  erroDatasReserva: string | null = null;

  minDate = new Date();
  maxDate = new Date(new Date().getFullYear() + 1, 11, 31);

  valorDiariaFormatado = '';

  filtroRapidoAtivo: 'hoje' | '7' | 'mes' | 'todos' | '' = 'todos';

  constructor(
    private reservasService: ReservaService,
    private toast: ToastService
    , private modalScroll: ModalScrollService
  ) {}

  ngOnInit(): void {
    // Iniciar mostrando todas as reservas
    this.aplicarFiltroRapido('todos');
  }

  // =========================================================
  // =================== HELPERS DE TIPO =====================
  // =========================================================

  private coerceTipoCliente(valor: string | TipoCliente | null | undefined): TipoCliente {
    if (!valor) return 'COMUM';
    const s = String(valor || '').toUpperCase();
    if (s === 'PREFEITURA') return 'PREFEITURA';
    if (s === 'CORPORATIVO' || s === 'JURIDICA' || s === 'JURÍDICA') return 'CORPORATIVO';
    if (s === 'COMUM' || s === 'FISICA' || s === 'FÍSICA' || s === 'HOSPEDE') return 'COMUM';
    return 'COMUM';
  }

  // =========================================================
  // =================== GETTERS / FORMAT ====================
  // =========================================================

  private formatar(d?: string | null): string {
    const dt = this.parseLocalYMD(d || '');
    return dt ? dt.toLocaleDateString('pt-BR') : '-';
  }

  private norm(s: any): string {
    return (s ?? '').toString().trim();
  }

  private eqDateStr(a?: string | null, b?: string | null): boolean {
    const A = (a ?? '').toString().split('T')[0];
    const B = (b ?? '').toString().split('T')[0];
    return A === B;
  }

  public getNome(r: Reserva): string {
    return r?.nome || `Reserva ${r?.codigo ?? r?.id ?? ''}`;
  }

  public getNumeroQuarto(r: Reserva): string {
    return r?.numeroQuarto || '-';
  }

  public getPeriodo(r: Reserva): string {
    const di = this.formatar(r?.dataEntrada);
    const df = this.formatar(r?.dataSaida);
    if (di === '-' && df === '-') return '-';
    if (di !== '-' && df !== '-') return `${di} — ${df}`;
    return di !== '-' ? di : df;
  }

  get selecionadaValorDiaria(): number {
    return Number(this.selecionada?.valorDiaria ?? 0);
  }

  get selecionadaTotal(): number {
    const r = this.selecionada;
    if (r?.valorTotal != null) return Number(r.valorTotal);
    const vd = Number(r?.valorDiaria ?? 0);
    const nd = Number(r?.numeroDiarias ?? 1);
    return vd * nd;
  }

  get selecionadaFormaPagamento(): string {
    return this.selecionada?.formaPagamento || '—';
  }

  public hasDataReserva(r: Reserva): boolean {
    return !!(r && r.dataReserva);
  }

  public getDataReserva(r: Reserva): string {
    return this.formatar(r?.dataReserva ?? null);
  }

  public isCancelada(r: Reserva | null | undefined): boolean {
    if (!r) return false;
    return (r.status || '').toUpperCase() === 'CANCELADA';
  }

  public getCanceladoPor(r: Reserva | null | undefined): string {
    if (!r) return '';
    return (
      (r as any).canceladoPor ||
      (r as any).usuarioCancelamento ||
      (r as any).cancelledBy ||
      (r as any).canceladorNome ||
      (r as any).cancelador ||
      (r as any).canceladorId ||
      ''
    );
  }

  public getDataCancelamento(r: Reserva | null | undefined): string {
    if (!r) return '';
    const raw =
      (r as any).dataCancelamento ||
      (r as any).cancelledAt ||
      (r as any).dtCancelamento ||
      (r as any).canceladoEm ||
      (r as any).cancelado_em ||
      '';
    if (!raw) return '';
    const dt = this.parseLocalYMD(raw);
    return dt ? dt.toLocaleDateString('pt-BR') : '';
  }

  public getMotivoCancelamento(r: Reserva | null | undefined): string {
    if (!r) return '';
    const m =
      (r as any).motivoCancelamento ||
      (r as any).motivo ||
      (r as any).observacaoCancelamento ||
      (r as any).motivoCancel ||
      (r as any).observacoesCancelamento ||
      '';
    if (typeof m === 'string') {
      const s = (m || '').toString().trim();
      if (!s) return '';
      if (s === '{}' || s === '[]' || s.toLowerCase() === 'null' || s === 'undefined') return '';
      try {
        const parsed = JSON.parse(s);
        if (parsed === null) return '';
        if (typeof parsed === 'string') {
          const ps = parsed.trim();
          if (!ps || ps === '{}' || ps === '[]') return '';
          return ps;
        }
        if (typeof parsed === 'object') {
          const candidates = [
            (parsed as any).motivo,
            (parsed as any).mensagem,
            (parsed as any).message,
            (parsed as any).observacao,
            (parsed as any).observacoes,
            (parsed as any).reason,
            (parsed as any).texto,
            (parsed as any).descricao,
          ];
          for (const c of candidates) {
            if (typeof c === 'string' && c.trim()) return c.trim();
          }
          if (Object.keys(parsed).length === 0) return '';
          return JSON.stringify(parsed);
        }
      } catch {
        // não é json
      }
      return s;
    }
    if (typeof m === 'number' || typeof m === 'boolean') return String(m);
    if (typeof m === 'object' && m !== null) {
      const candidates = [
        (m as any).motivo,
        (m as any).mensagem,
        (m as any).message,
        (m as any).observacao,
        (m as any).observacoes,
        (m as any).reason,
        (m as any).texto,
        (m as any).descricao,
      ];
      for (const c of candidates) {
        if (typeof c === 'string' && c.trim()) return c.trim();
      }
      if (Object.keys(m).length === 0) return '';
      try {
        const s = JSON.stringify(m);
        if (s === '{}' || s === 'null' || s === '""') return '';
        return s;
      } catch {
        return '';
      }
    }
    return '';
  }

  public getCriadoPor(r: Reserva | null | undefined): string {
    if (!r) return '';
    return (
      (r as any).criadoPor ||
      (r as any).usuarioCriacao ||
      (r as any).createdBy ||
      (r as any).autorNome ||
      (r as any).autor ||
      (r as any).autorId ||
      ''
    );
  }

  public getEmailAlias(r: Reserva | null | undefined): string {
    if (!r) return '';
    const email = (r as any).email || '';
    const nome = (r as any).nome || '';
    if (email && nome) return `${nome} <${email}>`;
    return email || '';
  }

  get dias(): number {
    if (!this.dataInicio || !this.dataFim) return 0;
    const ms = this.dataFim.getTime() - this.dataInicio.getTime();
    const d = Math.ceil(ms / (1000 * 60 * 60 * 24));
    return Number.isFinite(d) && d > 0 ? d : 0;
  }

  get total(): number {
    const v = Number(this.reservaModel.valorDiaria ?? 0);
    return v * (this.dias || 0);
  }

  get labelDiarias(): string {
    const d = this.dias || 0;
    return d === 1 ? '1 diária' : `${d} diárias`;
  }

  getMinDataFim(): string {
    if (this.dataInicio) {
      const diaSeguinte = new Date(this.dataInicio.getTime() + 86400000);
      return diaSeguinte.toISOString().split('T')[0];
    }
    return this.todayYMDLocal();
  }

  public podeFazerCheckin(reserva: Reserva): boolean {
    if (!reserva || reserva.status !== 'PENDENTE' || !reserva.dataEntrada) return false;
    const entradaYMD = this.onlyYMD(reserva.dataEntrada);
    const hojeYMD = this.todayYMDLocal();
    return entradaYMD === hojeYMD;
  }

  // =========================================================
  // =================== BUSCA / FILTROS =====================
  // =========================================================

  onChangeBusca(_: string) {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.buscar(), 200);
  }

  onFiltroRangeChange(): void {
    if (!this.filtroRange) {
      this.filtroInicio = '';
      this.filtroFim = '';
      this.filtroDataInicio = '';
      this.filtroDataFim = '';
      this.buscar();
      return;
    }

    const start: Date | null = this.filtroRange?.start ?? this.filtroRange?.begin ?? null;
    const end: Date | null =
      this.filtroRange?.end ?? this.filtroRange?.finish ?? this.filtroRange?._end ?? null;

    const toYMD = (d: Date | null): string => {
      if (!d) return '';
      try {
        return d.toISOString().split('T')[0];
      } catch {
        return '';
      }
    };

    const startStr = toYMD(start);
    const endStr = toYMD(end);

    this.filtroInicio = startStr;
    this.filtroFim = endStr;
    this.filtroDataInicio = startStr;
    this.filtroDataFim = endStr;

    this.buscar();
  }

  limparFiltros(): void {
    this.filtroBusca = '';
    this.filtroInicio = '';
    this.filtroFim = '';
    this.filtroDataInicio = '';
    this.filtroDataFim = '';
    this.filtroStatus = '';
    this.filtroRapidoAtivo = 'todos';
    this.pagina = 1;
    this.buscar();
  }

  buscar(): void {
    this.pagina = 1;
    this.carregandoLista = true;
    this.estaBuscandoReservas = true;

    this.reservasService.listar().subscribe({
      next: (arr) => {
        const busca = (this.filtroBusca || '').toLowerCase().trim();
        const statusFiltro = (this.filtroStatus || '').toUpperCase().trim();

        const inicioStr = this.filtroInicio || this.filtroDataInicio || '';
        const fimStr = this.filtroFim || this.filtroDataFim || '';

        const iFiltro = inicioStr ? this.parseLocalYMD(inicioStr) : null;
        const fFiltro = fimStr ? this.parseLocalYMD(fimStr) : null;

        const filtradas = (arr || []).filter((r: Reserva) => {
          // --------- BUSCA TEXTUAL ---------
          const matchBusca =
            !busca ||
            r.nome?.toLowerCase().includes(busca) ||
            r.codigo?.toString().toLowerCase().includes(busca) ||
            r.cpf?.toLowerCase().includes(busca) ||
            r.email?.toLowerCase().includes(busca) ||
            (r.tipoCliente as any)?.toString().toLowerCase().includes(busca);

          // --------- STATUS (case-insensitive) ---------
          const statusReserva = (r.status || '').toUpperCase();
          const matchStatus = !statusFiltro || statusReserva === statusFiltro;

          // --------- PERÍODO ---------
          const dentroPeriodo = () => {
            // Se não tem filtro de data, mostra tudo
            if (!iFiltro && !fFiltro) return true;

            const di = r.dataEntrada ? this.parseLocalYMD(r.dataEntrada) : null;
            const df = r.dataSaida ? this.parseLocalYMD(r.dataSaida) : null;

            // Se a reserva não tem datas válidas, não mostra
            if (!di || !df) return false;

            // Lógica mais flexível: mostra reservas que tenham ANY sobreposição com o período filtrado
            if (iFiltro && fFiltro) {
              // Reserva inicia ANTES do fim do filtro E termina DEPOIS do início do filtro
              return di <= fFiltro && df >= iFiltro;
            } else if (iFiltro) {
              // Apenas data início: mostra reservas que terminam depois da data início
              return df >= iFiltro;
            } else if (fFiltro) {
              // Apenas data fim: mostra reservas que começam antes da data fim
              return di <= fFiltro;
            }

            return true;
          };

          return matchBusca && matchStatus && dentroPeriodo();
        });

        // guarda todas as reservas filtradas
        this.reservasAll = filtradas || [];
        this.reservasFiltradas = [...this.reservasAll];

        this.totalElementos = this.reservasAll.length;
        this.totalPaginas = Math.max(1, Math.ceil(this.totalElementos / this.tamanho));

        if (this.pagina > this.totalPaginas) {
          this.pagina = this.totalPaginas;
        }

        const start = (this.pagina - 1) * this.tamanho;
        this.reservas = this.reservasAll.slice(start, start + this.tamanho);

        this.carregandoLista = false;
        this.estaBuscandoReservas = false;
      },
      error: (e) => {
        this.carregandoLista = false;
        this.estaBuscandoReservas = false;
        console.error('Erro ao buscar reservas:', e);
        this.toast.error(this.extrairMensagemErro(e) || 'Falha ao carregar reservas.');
      },
    });
  }

  private extrairMensagemErro(error: any): string {
    if (error?.error?.message) {
      return error.error.message;
    }
    if (error?.message) {
      return error.message;
    }
    if (typeof error === 'string') {
      return error;
    }
    return 'Erro desconhecido';
  }

  // navegação de páginas (cliente)
  irPara(p: number) {
    if (!p || p < 1) p = 1;
    if (p > this.totalPaginas) p = this.totalPaginas;
    if (p === this.pagina) return;
    this.pagina = p;
    const start = (this.pagina - 1) * this.tamanho;
    this.reservas = (this.reservasAll || []).slice(start, start + this.tamanho);
    // scroll sutil para a tabela (útil em dispositivos móveis)
    try {
      const el = document.querySelector('[role="table"]');
      if (el) (el as HTMLElement).scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch {}
  }

  // =========================================================
  // =================== NOVA / EDITAR =======================
  // =========================================================

  abrirNovo(): void {
    this.isEditMode = false;
    this.submitting = false;
    this.estaProcessandoAcao = false;
    this.reservaEmProcessamentoId = null;

    this.reservaModel = {
      nome: '',
      telefone: '',
      cpf: '',
      email: '',
      tipoCliente: '',
      observacoes: '',
      valorDiaria: null,
      formaPagamento: '',
    };

    this.dataInicio = null;
    this.dataFim = null;
    this.dataInicioStr = '';
    this.dataFimStr = '';
    this.periodoRangeModel = { start: null, end: null };

    this.quartoNumero = '';
    this.quartos = [];
    this.erroDatasReserva = null;

    this.showCreate = true;
    this.valorDiariaFormatado = '';
    this.modalScroll.lock();
  }

  abrirModalEditar(reserva: Reserva): void {
    if (reserva.status !== 'PENDENTE') {
      this.toast.warning('Somente reservas PENDENTES podem ser editadas.');
      return;
    }

    this.selecionada = reserva;
    this.isEditMode = true;
    this.submitting = false;
    this.estaProcessandoAcao = false;
    this.reservaEmProcessamentoId = null;

    this.dataInicio = reserva.dataEntrada ? new Date(reserva.dataEntrada) : null;
    this.dataFim = reserva.dataSaida ? new Date(reserva.dataSaida) : null;

    this.dataInicioStr = this.dataInicio ? this.dataInicio.toISOString().split('T')[0] : '';
    this.dataFimStr = this.dataFim ? this.dataFim.toISOString().split('T')[0] : '';

    this.periodoRangeModel = {
      start: this.dataInicio,
      end: this.dataFim,
    };

    this.reservaModel = {
      nome: reserva.nome || '',
      telefone: reserva.telefone || '',
      cpf: reserva.cpf || '',
      email: reserva.email || '',
      tipoCliente: (reserva?.tipoCliente as any) ?? '',
      observacoes: reserva.observacoes || '',
      valorDiaria: reserva.valorDiaria ?? null,
      formaPagamento: (reserva.formaPagamento as any) || '',
    };

    this.quartoNumero = reserva.numeroQuarto || '';
    this.quartos = [];
    this.erroDatasReserva = null;
    this.showCreate = true;

    if (this.reservaModel.valorDiaria) {
      this.valorDiariaFormatado = Number(this.reservaModel.valorDiaria).toLocaleString('pt-BR', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });
    } else {
      this.valorDiariaFormatado = '';
    }

    setTimeout(() => {
      this.onDatasChangeNovo();
    }, 100);
    this.modalScroll.lock();
  }

  fecharNovo(): void {
    this.showCreate = false;
    this.isEditMode = false;
    this.submitting = false;
    this.estaProcessandoAcao = false;
    this.reservaEmProcessamentoId = null;
    this.selecionada = null;
    this.quartos = [];
    this.erroDatasReserva = null;
    this.modalScroll.unlock();
  }

  // =========================================================
  // =================== DETALHES ============================
  // =========================================================

  abrirDetalhes(r: Reserva): void {
    this.selecionada = r;
    this.showDetails = true;
    this.modalScroll.lock();
  }

  fecharDetalhes(): void {
    this.showDetails = false;
    this.selecionada = null;
    this.modalScroll.unlock();
  }

  // =========================================================
  // =================== CONFIRMAÇÃO =========================
  // =========================================================

  abrirModalConfirmacao(acao: TipoConfirmacao, reserva: Reserva) {
    this.acaoConfirmacao = acao;
    this.reservaParaConfirmar = reserva;

    this.executandoConfirmacao = false;

    if (acao === 'cancelar') {
      this.motivoCancelamentoModal = this.getMotivoCancelamento(reserva) || '';
      this.observacaoCancelamentoModal = '';
    } else {
      this.motivoCancelamentoModal = '';
      this.observacaoCancelamentoModal = '';
    }
    this.showConfirm = true;
    this.modalScroll.lock();
  }

  fecharModalConfirmacao() {
    this.showConfirm = false;
    this.acaoConfirmacao = null;
    this.reservaParaConfirmar = null;
    this.motivoCancelamentoModal = '';
    this.observacaoCancelamentoModal = '';
    this.executandoConfirmacao = false;
    this.linhaCarregandoId = null;
    this.linhaAcao = null;
    this.modalScroll.unlock();
  }

  confirmarAcao() {
    const r = this.reservaParaConfirmar;
    if (!r || !this.acaoConfirmacao) return;

    if (this.acaoConfirmacao === 'cancelar') {
      this.cancelar(r, (this.motivoCancelamentoModal || '').trim());
    } else if (this.acaoConfirmacao === 'checkin') {
      this.realizarCheckin(r);
    }
  }

  // =========================================================
  // =================== CONFIRMAR CHECK-IN ==================
  // =========================================================

  private realizarCheckin(reserva: Reserva): void {
    if (!reserva || !reserva.id) return;

    const payload: ConfirmarReservaPayload = {
      tipoCliente: this.coerceTipoCliente(reserva.tipoCliente),
      observacoesCheckin: '',
    };

    if (reserva.email?.trim()) payload.email = reserva.email.trim();
    if (reserva.cpf?.trim()) payload.cpf = reserva.cpf.trim();

    this.executandoConfirmacao = true;
    this.estaProcessandoAcao = true;
    this.reservaEmProcessamentoId = reserva.id;
    this.linhaCarregandoId = reserva.id;
    this.linhaAcao = 'checkin';

    this.reservasService.confirmar(reserva.id, payload).subscribe({
      next: () => {
        this.toast.success(`Check-in da reserva ${reserva.codigo || reserva.id} realizado!`);
        this.executandoConfirmacao = false;
        this.estaProcessandoAcao = false;
        this.reservaEmProcessamentoId = null;
        this.linhaCarregandoId = null;
        this.linhaAcao = null;
        this.fecharModalConfirmacao();
        this.buscar();
      },
      error: (e) => {
        this.executandoConfirmacao = false;
        this.estaProcessandoAcao = false;
        this.reservaEmProcessamentoId = null;
        this.linhaCarregandoId = null;
        this.linhaAcao = null;
        this.toast.error(this.extrairMensagemErro(e) || 'Erro ao realizar check-in.');
      },
    });
  }

  // =========================================================
  // =================== RANGE DO MODAL ======================
  // =========================================================

  onPeriodoRangeChangeModal(): void {
    this.dataInicio = this.periodoRangeModel.start
      ? new Date(this.periodoRangeModel.start)
      : null;
    this.dataFim = this.periodoRangeModel.end ? new Date(this.periodoRangeModel.end) : null;
    this.onDatasChangeNovo();
  }

  setDuracaoRapida(diasExtras: number) {
    const base = this.dataInicio
      ? new Date(this.dataInicio)
      : (() => {
          const hoje = new Date();
          hoje.setHours(0, 0, 0, 0);
          return hoje;
        })();

    const fim = new Date(base);
    fim.setDate(fim.getDate() + diasExtras);

    this.dataInicio = base;
    this.dataFim = fim;

    this.periodoRangeModel = {
      start: this.dataInicio,
      end: this.dataFim,
    };

    this.onDatasChangeNovo();
  }

  // =========================================================
  // =================== DATAS / QUARTOS =====================
  // =========================================================

  onDatasChangeNovo(): void {
    this.quartos = [];
    this.erroDatasReserva = null;

    if (!this.dataInicio || !this.dataFim) return;

    if (this.dataFim <= this.dataInicio) {
      this.erroDatasReserva = 'A data de check-out deve ser posterior à data de check-in.';
      return;
    }

    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);

    const dataInicio = new Date(this.dataInicio);
    dataInicio.setHours(0, 0, 0, 0);

    if (dataInicio < hoje) {
      this.erroDatasReserva = 'Data de entrada não pode ser anterior à data atual.';
      return;
    }

    this.carregandoQuartos = true;

    const dataInicioStr = this.dataInicio.toISOString().split('T')[0];
    const dataFimStr = this.dataFim.toISOString().split('T')[0];

    this.reservasService
      .listarQuartosDisponiveisPorPeriodo(dataInicioStr, dataFimStr)
      .subscribe({
        next: (qs) => {
          this.quartos = qs || [];

          if (this.isEditMode && this.selecionada && this.quartoNumero) {
            const existe = this.quartos.some((q) => q.numero === this.quartoNumero);
            if (!existe) {
              this.quartos.unshift({
                id: 0,
                numero: this.quartoNumero,
                tipo: 'Quarto Atual',
                valorDiaria: this.reservaModel.valorDiaria || 0,
              } as Quarto);
            }
          }

          this.carregandoQuartos = false;

          if (
            !this.isEditMode &&
            this.quartoNumero &&
            !this.quartos.some((q) => q.numero === this.quartoNumero)
          ) {
            this.quartoNumero = '';
          }
        },
        error: (e) => {
          this.carregandoQuartos = false;
          this.quartos = [];
          this.erroDatasReserva =
            this.extrairMensagemErro(e) || 'Erro ao buscar quartos disponíveis.';
        },
      });
  }

  onQuartoChange(numeroQuarto: string): void {
    const quartoSelecionado = this.quartos.find((q) => q.numero === numeroQuarto);
    if (!quartoSelecionado) return;

    const valor = quartoSelecionado.valorDiaria ?? null;
    this.reservaModel.valorDiaria = valor;
    this.valorDiariaFormatado = (valor ?? 0).toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  onModalDataInicioChange(val: string) {
    this.dataInicioStr = val || '';
    const parsed = this.parseLocalYMD(val);
    this.dataInicio = parsed || null;

    if ((val || '').toString().trim() && !parsed) {
      this.erroDatasReserva =
        'Data de check-in inválida ou fora do intervalo permitido (YYYY-MM-DD).';
      return;
    }

    this.erroDatasReserva = null;

    if (this.dataInicio && this.dataFim) this.onDatasChangeNovo();
  }

  onModalDataFimChange(val: string) {
    this.dataFimStr = val || '';
    const parsed = this.parseLocalYMD(val);
    this.dataFim = parsed || null;

    if ((val || '').toString().trim() && !parsed) {
      this.erroDatasReserva =
        'Data de check-out inválida ou fora do intervalo permitido (YYYY-MM-DD).';
      return;
    }

    this.erroDatasReserva = null;

    if (this.dataInicio && this.dataFim) this.onDatasChangeNovo();
  }

  // =========================================================
  // =================== VALIDAÇÕES ==========================
  // =========================================================

  private validarObrigatorios(isEdicao: boolean): string | null {
    const nomeOk = !!this.norm(this.reservaModel.nome);
    if (!nomeOk) return 'Informe o nome do hóspede.';

    if (!this.dataInicio) return 'Informe a data de check-in.';
    if (!this.dataFim) return 'Informe a data de check-out.';

    if (this.dataFim <= this.dataInicio) {
      return 'A data de check-out deve ser posterior à data de check-in.';
    }

    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);

    const dataInicio = new Date(this.dataInicio);
    dataInicio.setHours(0, 0, 0, 0);

    if (dataInicio < hoje) {
      return 'Data de entrada não pode ser anterior à data atual.';
    }

    if (!isEdicao && !this.quartoNumero) {
      return 'Selecione o quarto.';
    }

    const vd = Number(this.reservaModel.valorDiaria ?? 0);
    if (!vd || vd <= 0) {
      return 'Informe um valor de diária válido.';
    }

    if (!this.reservaModel.formaPagamento) {
      return 'Selecione a forma de pagamento.';
    }

    if (!this.reservaModel.tipoCliente) {
      return 'Selecione o tipo de cliente.';
    }

    if (this.reservaModel.telefone) {
      if (!this.telefoneOpcionalValido(this.reservaModel.telefone)) {
        return 'Telefone deve conter exatamente 11 dígitos.';
      }
    }

    return null;
  }

  onEmailInput(ev: Event) {
    const value = (ev.target as HTMLInputElement).value || '';
    this.reservaModel.email = value;

    const atIndex = value.indexOf('@');
    if (atIndex < 0) {
      this.emailHintsOpen = false;
      this.emailCandidates = [];
      return;
    }

    const local = value.slice(0, atIndex).trim();
    const after = value.slice(atIndex + 1).toLowerCase();

    if (!local) {
      this.emailHintsOpen = false;
      this.emailCandidates = [];
      return;
    }

    const list = !after ? this.commonDomains : this.commonDomains.filter((d) => d.startsWith(after));

    this.emailCandidates = list;
    this.emailActiveIndex = 0;
    this.emailHintsOpen = list.length > 0;
  }

  onEmailKeydown(ev: KeyboardEvent) {
    if (!this.emailHintsOpen || !this.emailCandidates.length) return;

    switch (ev.key) {
      case 'ArrowDown':
        ev.preventDefault();
        this.emailActiveIndex = (this.emailActiveIndex + 1) % this.emailCandidates.length;
        break;
      case 'ArrowUp':
        ev.preventDefault();
        this.emailActiveIndex =
          (this.emailActiveIndex - 1 + this.emailCandidates.length) % this.emailCandidates.length;
        break;
      case 'Enter':
      case 'Tab':
        ev.preventDefault();
        this.applyEmailDomain(this.emailCandidates[this.emailActiveIndex]);
        break;
      case 'Escape':
        this.closeEmailHints();
        break;
    }
  }

  onEmailBlur() {
    setTimeout(() => this.closeEmailHints(), 100);
  }

  applyEmailDomain(domain: string) {
    const value = (this.reservaModel.email || '').toString();
    const atIndex = value.indexOf('@');
    if (atIndex < 0) return;

    const local = value.slice(0, atIndex).trim();
    this.reservaModel.email = `${local}@${domain}`;
    this.closeEmailHints();
  }

  private closeEmailHints() {
    this.emailHintsOpen = false;
    this.emailCandidates = [];
    this.emailActiveIndex = 0;
  }

  // =========================================================
  // =================== TELEFONE ============================
  // =========================================================

  private somenteDigitos(v: string | null | undefined): string {
    return (v || '').toString().replace(/\D/g, '');
  }

  formatarTelefonePadrao(digs: string): string {
    const d = (digs || '').slice(0, 11);
    if (!d) return '';
    const dd = d.slice(0, 2);
    const p1 = d.slice(2, 7);
    const p2 = d.slice(7, 11);
    if (d.length <= 2) return `(${dd}`;
    if (d.length <= 7) return `(${dd}) ${p1}`;
    return `(${dd}) ${p1}-${p2}`;
  }

  bloquearNaoDigitos(ev: KeyboardEvent) {
    const k = ev.key;
    const allowed = ['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab'];
    if (allowed.includes(k)) return;
    if (ev.ctrlKey || (ev as any).metaKey) return;
    if (!/[0-9]/.test(k)) ev.preventDefault();
  }

  onTelefonePaste(ev: ClipboardEvent) {
    ev.preventDefault();
    const text = ev.clipboardData?.getData('text') || '';
    const digs = this.somenteDigitos(text).slice(0, 11);
    const formatted = this.formatarTelefonePadrao(digs);
    const target = ev.target as HTMLInputElement;
    if (target) target.value = formatted;
    this.reservaModel.telefone = formatted;
  }

  onTelefoneInput(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const digs = this.somenteDigitos(input.value).slice(0, 11);
    input.value = this.formatarTelefonePadrao(digs);
    this.reservaModel.telefone = input.value;
  }

  telefoneOpcionalValido(v: string | null | undefined): boolean {
    const d = this.somenteDigitos(v || '');
    return d.length === 11;
  }

  // =========================================================
  // =================== CPF ================================
  // =========================================================

  formatarCpf(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');

    if (value.length > 11) {
      value = value.substring(0, 11);
    }

    if (value.length > 9) {
      value = value.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
    } else if (value.length > 6) {
      value = value.replace(/(\d{3})(\d{3})(\d{1,3})/, '$1.$2.$3');
    } else if (value.length > 3) {
      value = value.replace(/(\d{3})(\d{1,3})/, '$1.$2');
    }

    input.value = value;
    this.reservaModel.cpf = value;
  }

  // =========================================================
  // =================== SALVAR ==============================
  // =========================================================

  private buildBasePayload(): {
    nome: string;
    numeroQuarto: string;
    dataEntrada: string;
    numeroDiarias: number;
    observacoes: string;
    telefone: string;
    cpf: string;
    email: string;
    tipoCliente: TipoCliente;
    valorDiaria: number;
    formaPagamento: 'DINHEIRO' | 'DEBITO' | 'CREDITO' | 'PIX';
  } {
    const dataEntradaStr = this.dataInicio ? this.dataInicio.toISOString().split('T')[0] : '';
    return {
      nome: this.norm(this.reservaModel.nome),
      numeroQuarto: String(this.quartoNumero || this.selecionada?.numeroQuarto || ''),
      dataEntrada: dataEntradaStr,
      numeroDiarias: this.dias,
      observacoes: this.norm(this.reservaModel.observacoes),
      telefone: this.norm(this.reservaModel.telefone),
      cpf: this.norm(this.reservaModel.cpf),
      email: this.norm(this.reservaModel.email),
      tipoCliente: this.coerceTipoCliente(this.reservaModel.tipoCliente),
      valorDiaria: Number(this.reservaModel.valorDiaria),
      formaPagamento: this.reservaModel.formaPagamento as
        | 'DINHEIRO'
        | 'DEBITO'
        | 'CREDITO'
        | 'PIX',
    };
  }

  private houveAlteracao(reserva: Reserva, payload: AtualizarReservaPayload): boolean {
    const atual = {
      nome: this.norm(reserva.nome),
      numeroQuarto: String(reserva.numeroQuarto || ''),
      dataEntrada: (reserva.dataEntrada || '').toString().split('T')[0],
      numeroDiarias: Number(reserva.numeroDiarias || 0),
      observacoes: this.norm(reserva.observacoes),
      telefone: this.norm(reserva.telefone),
      cpf: this.norm(reserva.cpf),
      email: this.norm(reserva.email),
      tipoCliente: this.coerceTipoCliente(reserva.tipoCliente),
      valorDiaria: Number(reserva.valorDiaria ?? 0),
      formaPagamento: this.norm(reserva.formaPagamento),
    };

    const novo = {
      nome: this.norm(payload.nome),
      numeroQuarto: String(payload.numeroQuarto || ''),
      dataEntrada: (payload.dataEntrada || '').toString().split('T')[0],
      numeroDiarias: Number(payload.numeroDiarias || 0),
      observacoes: this.norm(payload.observacoes),
      telefone: this.norm(payload.telefone),
      cpf: this.norm(payload.cpf),
      email: this.norm(payload.email),
      tipoCliente: this.coerceTipoCliente(payload.tipoCliente),
      valorDiaria: Number(payload.valorDiaria ?? 0),
      formaPagamento: this.norm(payload.formaPagamento),
    };

    return JSON.stringify(atual) !== JSON.stringify(novo);
  }

  get desabilitarSubmit(): boolean {
    if (this.submitting || this.estaProcessandoAcao) return true;
    const msg = this.validarObrigatorios(this.isEditMode);
    if (msg) return true;

    if (this.isEditMode && this.selecionada) {
      const payload: AtualizarReservaPayload = {
        id: this.selecionada.id,
        ...this.buildBasePayload(),
      };
      return !this.houveAlteracao(this.selecionada, payload);
    }

    return false;
  }

  salvarNovo(f: NgForm): void {
    const msgErro = this.validarObrigatorios(this.isEditMode);
    if (msgErro) {
      this.toast.warning(msgErro);
      return;
    }

    const base = this.buildBasePayload();

    if (this.isEditMode) {
      if (!this.selecionada?.id) {
        this.toast.error('Reserva alvo não encontrada para edição.');
        return;
      }

      const payload: AtualizarReservaPayload = {
        id: this.selecionada.id,
        ...base,
      };

      if (!this.houveAlteracao(this.selecionada, payload)) {
        this.toast.info('Nenhuma alteração detectada.');
        return;
      }

      this.submitting = true;
      this.estaProcessandoAcao = true;
      this.reservaEmProcessamentoId = this.selecionada.id;

      this.reservasService.atualizar(this.selecionada.id, payload).subscribe({
        next: () => {
          this.toast.success(
            `Reserva ${this.selecionada?.codigo || this.selecionada?.id} atualizada!`
          );
          this.submitting = false;
          this.estaProcessandoAcao = false;
          this.reservaEmProcessamentoId = null;
          this.showCreate = false;
          this.buscar();
        },
        error: (e) => {
          this.submitting = false;
          this.estaProcessandoAcao = false;
          this.reservaEmProcessamentoId = null;
          this.toast.error(this.extrairMensagemErro(e) || 'Erro ao atualizar reserva.');
        },
      });

      return;
    }

    const payloadCriar: CriarReservaPayload = { ...base };
    this.submitting = true;
    this.estaProcessandoAcao = true;
    this.reservaEmProcessamentoId = 'nova';

    this.reservasService.criar(payloadCriar).subscribe({
      next: (reservaCriada) => {
        const reservaFinal: Reserva = reservaCriada
          ? reservaCriada
          : {
              id: 0,
              codigo: '',
              nome: payloadCriar.nome,
              telefone: payloadCriar.telefone,
              cpf: payloadCriar.cpf,
              email: payloadCriar.email,
              tipoCliente: payloadCriar.tipoCliente,
              numeroQuarto: payloadCriar.numeroQuarto,
              dataEntrada: payloadCriar.dataEntrada,
              dataSaida: this.dataFim ? this.dataFim.toISOString().split('T')[0] : '',
              numeroDiarias: payloadCriar.numeroDiarias,
              status: 'CONFIRMADA',
              observacoes: payloadCriar.observacoes,
              valorDiaria: payloadCriar.valorDiaria,
              formaPagamento: payloadCriar.formaPagamento,
            } as Reserva;

        this.submitting = false;
        this.estaProcessandoAcao = false;
        this.reservaEmProcessamentoId = null;
        this.showCreate = false;

        // guarda pra mostrar no modal e pra gerar o PDF
        this.reservaCriada = reservaFinal;
        this.selecionada = reservaFinal; // pra PDF funcionar
        this.showCreateSuccess = true;
        this.modalScroll.lock();

        // atualiza a lista
        this.buscar();

        this.toast.success('Reserva do hóspede criada com sucesso!');
      },
      error: (e) => {
        this.submitting = false;
        this.estaProcessandoAcao = false;
        this.reservaEmProcessamentoId = null;
        this.toast.error(this.extrairMensagemErro(e) || 'Erro ao criar reserva.');
      },
    });
  }

  // =========================================================
  // =================== AÇÕES BACKEND =======================
  // =========================================================

  private cancelar(r: Reserva, motivo?: string): void {
    if (!r?.id) return;

    const texto = (motivo || '').trim();

    this.executandoConfirmacao = true;
    this.estaProcessandoAcao = true;
    this.reservaEmProcessamentoId = r.id;
    this.linhaCarregandoId = r.id;
    this.linhaAcao = 'cancelar';

    this.reservasService.cancelar(r.id, texto).subscribe({
      next: () => {
        this.toast.success(`Reserva ${r.codigo || r.id} cancelada!`);
        this.executandoConfirmacao = false;
        this.estaProcessandoAcao = false;
        this.reservaEmProcessamentoId = null;
        this.linhaCarregandoId = null;
        this.linhaAcao = null;
        this.fecharModalConfirmacao();
        this.buscar();
      },
      error: (e) => {
        this.executandoConfirmacao = false;
        this.estaProcessandoAcao = false;
        this.reservaEmProcessamentoId = null;
        this.linhaCarregandoId = null;
        this.linhaAcao = null;
        this.toast.error(this.extrairMensagemErro(e) || 'Erro ao cancelar reserva.');
      },
    });
  }

  baixarComprovanteReservaPDF(): void {
    const r = this.selecionada || this.reservaCriada;
    if (!r) {
      this.toast.warning('Selecione uma reserva para gerar o comprovante.');
      return;
    }

    this.downloadingComprovante = true;

    const antigo = document.getElementById('reserva-comprovante-temp');
    if (antigo) antigo.remove();

    const element = document.createElement('div');
    element.id = 'reserva-comprovante-temp';
    element.style.position = 'fixed';
    element.style.top = '-9999px';
    element.style.left = '0';
    element.style.width = '440px';
    element.innerHTML = this.renderComprovanteReservaHtml(r);
    document.body.appendChild(element);

    setTimeout(() => {
      html2canvas(element, {
        scale: 2,
        useCORS: true,
        backgroundColor: '#ffffff',
      })
        .then((canvas) => {
          const imgData = canvas.toDataURL('image/png');
          const pdf = new jsPDF({
            orientation: 'portrait',
            unit: 'pt',
            format: [440, 650],
          });
          pdf.addImage(imgData, 'PNG', 8, 8, 420, 630, '', 'FAST');
          pdf.save(`Comprovante_Reserva_${r.codigo || r.id}.pdf`);
          element.remove();
          this.toast.success('Comprovante gerado com sucesso!');
          this.downloadingComprovante = false;
        })
        .catch(() => {
          element.remove();
          this.toast.error('Falha ao gerar o comprovante.');
          this.downloadingComprovante = false;
        });
    }, 150);
  }

  private renderComprovanteReservaHtml(r: Reserva): string {
    const dataReserva = this.hasDataReserva(r) ? this.getDataReserva(r) : undefined;
    const valorDiaria = r.valorDiaria ?? 0;
    const total = r.valorTotal ?? Number(valorDiaria) * (Number(r.numeroDiarias) || 1);
    const forma = r.formaPagamento || '-';
    const cancelada = this.isCancelada(r);
    const canceladoPor = this.getCanceladoPor(r);
    const dataCancelamento = this.getDataCancelamento(r);
    const motivoCancelamento = (this.getMotivoCancelamento(r) || '').trim();
    const criadoPor = this.getCriadoPor(r);

    return `
<div style="position:relative; font-family:'Helvetica Neue', Helvetica, Arial, sans-serif; width:380px; max-width:100%; margin:0 auto; padding:20px; background:#fff; color:#1f2937; overflow:hidden; border:1px solid #e5e7eb; border-radius:8px;">

  <div style="text-align:center; margin-bottom:15px; padding-bottom:10px; border-bottom:1px solid #e5e7eb;">
    <img src="assets/logo.png" alt="Logo" style="height:120px; max-width:220px; display:block; margin:0 auto 8px auto;" />
    <div style="font-size:11px; color:#9ca3af;">CNPJ: 12.886.443/0001-00</div>
  </div>

  <div style="font-size:18px; color:${
    cancelada ? '#b91c1c' : '#233642'
  }; text-align:center; font-weight:700; margin-bottom:20px; letter-spacing:0.5px; border-bottom:2px solid ${
      cancelada ? '#fecaca' : '#233642'
    }; padding-bottom:5px;">
    ${cancelada ? 'COMPROVANTE DE RESERVA (CANCELADA)' : 'COMPROVANTE DE RESERVA'}
  </div>

  <div style="margin-bottom:20px; padding-bottom:10px; border-bottom:1px dashed #d1d5db;">
    <div style="font-size:15px; margin-bottom:5px;">
      <b style="color:#4b5563;">Hóspede:</b>
      <span style="font-weight:700; color:#1f2937;">${r.nome ?? '-'}</span>
    </div>
    <div style="font-size:15px;">
      <b style="color:#4b5563;">Código da Reserva:</b>
      <span style="font-weight:600;">${r.codigo ?? '-'}</span>
    </div>
    ${
      criadoPor
        ? `
    <div style="font-size:13px; margin-top:4px; color:#6b7280;">
      Criada por: <b>${criadoPor}</b>
    </div>
    `
        : ''
    }
    ${
      this.getEmailAlias(r)
        ? `
    <div style="font-size:12px; margin-top:3px; color:#94a3b8;">
      ${this.getEmailAlias(r)}
    </div>
    `
        : ''
    }
  </div>

  <div style="margin-bottom:20px; padding-bottom:10px; border-bottom:1px dashed #d1d5db;">
    <div style="display:flex; justify-content:space-between; margin-bottom:8px;">
      <div style="font-size:15px; width:50%;">
        <div style="font-weight:600; color:#4b5563; margin-bottom:3px;">Check-in</div>
        <div style="font-weight:700; color:#1f2937;">${this.formatar(r.dataEntrada)}</div>
      </div>
      <div style="font-size:15px; width:50%;">
        <div style="font-weight:600; color:#4b5563; margin-bottom:3px;">Check-out</div>
        <div style="font-weight:700; color:#1f2937;">${this.formatar(r.dataSaida)}</div>
      </div>
    </div>

    <div style="display:flex; justify-content:space-between; margin-bottom:8px;">
      <div style="font-size:15px; width:50%;">
        <div style="font-weight:600; color:#4b5563; margin-bottom:3px;">Quarto</div>
        <div style="font-weight:700; color:#1f2937;">${r.numeroQuarto ?? '-'}</div>
      </div>
      <div style="font-size:15px; width:50%;">
        <div style="font-weight:600; color:#4b5563; margin-bottom:3px;">Diárias</div>
        <div style="font-weight:700; color:#1f2937;">${r.numeroDiarias ?? '-'}</div>
      </div>
    </div>

    ${
      dataReserva
        ? `
    <div style="font-size:15px; margin-bottom:8px;">
      <div style="font-weight:600; color:#4b5563; margin-bottom:3px;">Data da Reserva</div>
      <div style="font-weight:700; color:#1f2937;">${dataReserva}</div>
    </div>
    `
        : ''
    }
  </div>

  <div style="margin-bottom:20px; padding-bottom:10px; border-bottom:1px dashed #d1d5db;">
    <div style="font-size:15px; margin-bottom:8px;">
      <div style="font-weight:600; color:#4b5563; margin-bottom:3px;">Valor da Diária</div>
      <div style="font-weight:700; color:#1f2937;">R$ ${Number(valorDiaria)
        .toFixed(2)
        .replace('.', ',')}</div>
    </div>
    <div style="font-size:15px;">
      <div style="font-weight:600; color:#4b5563; margin-bottom:3px;">Forma de Pagamento</div>
      <div style="font-weight:700; color:#1f2937;">${forma}</div>
    </div>
  </div>

  <div style="text-align:right; margin-bottom:20px; padding-top:10px; border-top:2px solid ${
    cancelada ? '#fecaca' : '#233642'
  };">
    <div style="font-size:16px; color:${cancelada ? '#b91c1c' : '#233642'}; font-weight:700; margin-bottom:4px;">
      TOTAL ESTIMADO
    </div>
    <div style="font-size:24px; color:${cancelada ? '#ef4444' : '#10b981'}; font-weight:800;">
      R$ ${Number(total).toFixed(2).replace('.', ',')}
    </div>
  </div>

  ${
    r.observacoes
      ? `
  <div style="margin-bottom:20px; padding:8px 10px; background:#f9fafb; border-radius:8px; border:1px solid #e5e7eb; min-height:40px;">
    <div style="font-size:12px; font-weight:600; color:#4b5563; margin-bottom:2px;">Observações:</div>
    <div style="font-size:13px; color:#374151; white-space:pre-line;">
      ${String(r.observacoes).replace(/\n/g, '<br/>')}
    </div>
  </div>
  `
      : ''
  }

  ${
    cancelada
      ? `
  <div style="margin-bottom:14px; padding:8px 10px; background:rgba(254, 226, 226, 0.85); border:1px solid rgba(248,113,113,0.4); border-radius:6px;">
    <div style="font-size:12px; font-weight:700; color:#b91c1c; margin-bottom:2px;">Cancelada</div>
    <div style="font-size:12px; color:#7f1d1d;">
      ${canceladoPor ? `Cancelada por <b>${canceladoPor}</b>. ` : ''}
      ${dataCancelamento ? `em <b>${dataCancelamento}</b>. ` : ''}
      ${motivoCancelamento ? `<br/>Motivo: ${motivoCancelamento}` : ''}
    </div>
  </div>
  `
      : ''
  }

  <div style="font-size:11px; text-align:center; color:${
    cancelada ? '#b91c1c' : '#9ca3af'
  }; padding-top:10px; border-top:1px solid #e5e7eb;">
    ${
      cancelada
        ? `Este documento refere-se a uma reserva <b>cancelada</b> e não possui validade como documento oficial.`
        : `Este documento é válido como comprovante de reserva.`
    }<br/>
    Código ${r.codigo ?? 'N/A'} | Emitido em ${new Date().toLocaleDateString(
      'pt-BR'
    )} às ${new Date().toLocaleTimeString('pt-BR')}.
  </div>
</div>`;
  }

  fecharModalSucesso(): void {
    this.showCreateSuccess = false;
    this.reservaCriada = null;
    this.modalScroll.unlock();
  }

  // =========================================================
  // =================== VALOR DIÁRIA INPUT ==================
  // =========================================================

  onValorDiariaInput(event: Event) {
    const input = event.target as HTMLInputElement;
    let valor = input.value.replace(/\D/g, '');

    if (valor === '') {
      this.valorDiariaFormatado = '';
      this.reservaModel.valorDiaria = 0;
      return;
    }

    const numeroFormatado = parseFloat(valor) / 100;

    if (numeroFormatado > 10000) {
      const valorLimitado = 10000;
      this.valorDiariaFormatado = valorLimitado.toLocaleString('pt-BR', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });
      this.reservaModel.valorDiaria = valorLimitado;
      return;
    }

    this.valorDiariaFormatado = numeroFormatado.toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });

    this.reservaModel.valorDiaria = numeroFormatado;
  }

  permitirSomenteNumeros(event: KeyboardEvent) {
    const charCode = event.which ? event.which : event.keyCode ?? 0;
    const allow = ['Backspace', 'Tab', 'ArrowLeft', 'ArrowRight'];
    if (allow.includes(event.key)) return;
    if (charCode < 48 || charCode > 57) {
      event.preventDefault();
    }
  }

  // =========================================================
  // =================== HELPERS DATA ========================
  // =========================================================

  private parseLocalYMD(ymd?: string | null): Date | null {
    if (!ymd) return null;
    const only = String(ymd).split('T')[0].trim();
    const parts = only.split('-');
    if (parts.length !== 3) return null;
    const [yStr, mStr, dStr] = parts;
    const y = Number(yStr);
    const m = Number(mStr);
    const d = Number(dStr);

    if (!Number.isInteger(y) || !Number.isInteger(m) || !Number.isInteger(d)) return null;
    if (m < 1 || m > 12) return null;

    const daysInMonth = new Date(y, m, 0).getDate();
    if (d < 1 || d > daysInMonth) return null;

    const minY = this.minDate ? this.minDate.getFullYear() : 1900;
    const maxY = this.maxDate ? this.maxDate.getFullYear() : new Date().getFullYear() + 5;
    if (y < minY || y > maxY) return null;

    return new Date(y, m - 1, d);
  }

  todayYMDLocal(): string {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, '0');
    const d = String(now.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  private onlyYMD(s?: string | null): string {
    return (s ?? '').toString().split('T')[0];
  }

  // Util: formata para yyyy-MM-dd (padrão do <input type="date">)
  private fmt(d: Date): string {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  // Chamado quando o usuário altera manualmente as datas
  onFiltroDatasChange(): void {
    this.filtroRapidoAtivo = '';
    this.pagina = 1;
    this.buscar();
  }

  // Aplica os atalhos (Hoje / 7 dias / Mês / Todos)
  aplicarFiltroRapido(kind: 'hoje' | '7' | 'mes' | 'todos'): void {
    const hoje = new Date();
    let inicio = '';
    let fim = '';

    if (kind === 'hoje') {
      const d = this.fmt(hoje);
      inicio = d;
      fim = d;
    }

    if (kind === '7') {
      const start = new Date(hoje);
      start.setDate(start.getDate() - 6); // últimos 7 dias inclui hoje
      inicio = this.fmt(start);
      fim = this.fmt(hoje);
    }

    if (kind === 'mes') {
      const first = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
      const last = new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0);
      inicio = this.fmt(first);
      fim = this.fmt(last);
    }

    if (kind === 'todos') {
      inicio = '';
      fim = '';
    }

    this.filtroDataInicio = inicio;
    this.filtroDataFim = fim;
    this.filtroRapidoAtivo = kind;
    this.pagina = 1;

    this.buscar();
  }
}