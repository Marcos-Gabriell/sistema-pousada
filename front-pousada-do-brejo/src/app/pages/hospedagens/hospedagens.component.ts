import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { jsPDF } from 'jspdf';
import html2canvas from 'html2canvas';

import {
  HospedagemService,
  HospedagemResponse,
  EditarHospedagemDTO,
  QuartoCatalogo
} from './hospedagem.service';
import { ToastService } from '../../toast/toast.service';
import { AuthService } from '../../services/login/auth.service';
import { ModalScrollService } from '../../services/modal-scroll.service';

type FiltroRapido = 'HOJE' | '7DIAS' | 'MES' | 'TODOS';

@Component({
  selector: 'app-hospedagens',
  templateUrl: './hospedagens.component.html',
  standalone: true,
  imports: [CommonModule, FormsModule]
})
export class HospedagensComponent implements OnInit, OnDestroy {

  // ----------------- STATE PRINCIPAL -----------------
  quartosDisponiveis: { numero: string; tipo?: string; capacidade?: number }[] = [];

  private hospedagensBase: HospedagemResponse[] = [];
  hospedagens: HospedagemResponse[] = [];

  filtroNomeCpf = '';
  filtroStatus = '';
  filtroTipo = '';
  filtroDataInicio = '';
  filtroDataFim = '';
  filtroRapido: FiltroRapido = 'TODOS';

  mensagem = '';
  valorDiariaFormatado = '';

  paginaAtual = 1;
  itensPorPagina = 5;
  totalPaginas = 1;

  private searchTimer: any = null;

  // ----------------- MODAL FINALIZAR / SUCESSO CHECKOUT -----------------
  modalFinalizarAberto = false;
  hospedagemParaFinalizar: HospedagemResponse | null = null;
  descricaoFinalizar = '';
  temErroDescricao = false;

  modalSucessoCheckoutAberto = false;
  hospedagemFinalizada: HospedagemResponse | null = null;

  // ----------------- MODAL SUCESSO CHECKIN (CRIAÇÃO) -----------------
  modalSucessoCheckinAberto = false;
  hospedagemCriada: HospedagemResponse | null = null;

  // ----------------- NOVA / EDITAR / EXCLUIR -----------------
  mensagemErro = '';
  modalNovaHospedagemAberto = false;
  editandoHospedagem = false;
  hospedagemParaEditar: HospedagemResponse | null = null;
  
  // Estado de carregamento para ações (Checkin/Checkout/Edit/Delete)
  estaCarregando = false; 
  // Estado de carregamento para busca da tabela principal
  estaBuscandoHospedagens = false; // <<< NOVO

  // Estado de carregamento para a geração de PDF
  estaGerandoComprovante = false; 
  // id/identificador da hospedagem para a qual o comprovante está sendo gerado
  gerandoComprovanteParaId: string | number | null = null;

  // NOVA HOSPEDAGEM (com e-mail)
  novaHospedagem: {
    nome: string;
    cpf: string;
    email: string;
    numeroQuarto: string;
    numeroDiarias: string | number;
    valorDiaria: string | number;
    formaPagamento: string;
    observacoes: string;
    tipo: 'COMUM' | 'PREFEITURA' | 'CORPORATIVO' | '';
  } = {
    nome: '',
    cpf: '',
    email: '',
    numeroQuarto: '',
    numeroDiarias: '',
    valorDiaria: '',
    formaPagamento: '',
    observacoes: '',
    tipo: ''
  };

  formasPagamento = ['PIX', 'ESPÉCIE', 'CARTÃO'];

  // excluir
  modalExcluirAberto = false;
  hospedagemParaExcluir: HospedagemResponse | null = null;
  isAdminOuDev = false;

  // ---------- AUTOCOMPLETE DE E-MAIL ----------
  emailHintsOpen = false;
  emailActiveIndex = 0;
  emailCandidates: string[] = [];
  private readonly commonDomains = [
    'gmail.com',
    'outlook.com',
    'yahoo.com',
    'hotmail.com',
    'icloud.com',
    'live.com',
    'aol.com',
    'proton.me'
  ];

  // ---------- DETALHES ----------
  showDetalhes = false;
  hospedagemSelecionada: HospedagemResponse | null = null;

  // safe accessor para o template
  get hospedagemSelecionadaEmail(): string {
    return (this.hospedagemSelecionada as any)?.email || '—';
  }

  constructor(
    private hospedagemService: HospedagemService,
    private toast: ToastService,
    private auth: AuthService
    , private modalScroll: ModalScrollService
  ) {}

  // ----------------- CICLO DE VIDA -----------------

  ngOnInit(): void {
    this.isAdminOuDev = this.auth.isAdmin() || this.auth.isDev();
    this.selecionarFiltroRapido('TODOS');
    this.buscarHospedagensIniciais();
  }

  ngOnDestroy(): void {
    if (this.searchTimer) clearTimeout(this.searchTimer);
  }

  // ----------------- Utils / Normalizadores -----------------
  private _s(v: unknown): string {
    return String(v ?? '').trim();
  }

  private normalizeText(v: string): string {
    return (v || '')
      .toString()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
  }

  private onlyDigits(v: string): string {
    return (v || '').replace(/\D/g, '');
  }

  normalizeTipo(v: any): string {
    if (!v) return '';
    return String(v)
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toUpperCase()
      .trim();
  }

  normalizeStatus(v: any): string {
    if (v === true) return 'ATIVO';
    if (v === false) return 'INATIVO';
    return String(v || '').toUpperCase().trim();
  }

  isAtivo(h: HospedagemResponse): boolean {
    const s1 = this.normalizeStatus((h as any)?.status);
    const s2 = this.normalizeStatus((h as any)?.ativo as any);
    return s1 === 'ATIVO' || s2 === 'ATIVO';
  }

  isInativo(h: HospedagemResponse): boolean {
    const s1 = this.normalizeStatus((h as any)?.status);
    const s2 = this.normalizeStatus((h as any)?.ativo as any);
    return s1 === 'INATIVO' || s2 === 'INATIVO';
  }

  private parseYYYYMMDD(d: string | Date | undefined | null): string | null {
    if (!d) return null;
    try {
      if (typeof d === 'string') {
        if (/^\d{4}-\d{2}-\d{2}/.test(d)) return d.slice(0, 10);
        if (/^\d{2}\/\d{2}\/\d{4}$/.test(d)) {
          const [dd, mm, yyyy] = d.split('/');
          return `${yyyy}-${mm}-${dd}`;
        }
        // Tentativa de converter string ISO com horário para YYYY-MM-DD
        if (d.includes('T')) {
          const iso = new Date(d);
          if (!isNaN(iso.getTime())) return iso.toISOString().slice(0, 10);
        }
        const iso = new Date(d);
        if (!isNaN(iso.getTime())) return iso.toISOString().slice(0, 10);
        return null;
      } else {
        const iso = new Date(d);
        if (!isNaN(iso.getTime())) return iso.toISOString().slice(0, 10);
        return null;
      }
    } catch {
      return null;
    }
  }

  private inDateRange(dataEntrada: any, ini: string, fim: string): boolean {
    const d = this.parseYYYYMMDD(dataEntrada); // Garante formato YYYY-MM-DD
    if (!d) return false;
    // Comparações de strings YYYY-MM-DD funcionam como comparação de datas
    if (ini && d < ini) return false;
    if (fim && d > fim) return false;
    return true;
  }

  private ordenarPorMaisNovas(lista: HospedagemResponse[]) {
    const toTime = (x: HospedagemResponse) => {
      const v: any =
        (x as any)?.criadoEm ??
        x?.dataEntrada ??
        (x as any)?.createdAt ??
        (x as any)?.dataCadastro;

      if (!v) return 0;

      if (typeof v === 'string' && /^\d{2}\/\d{2}\/\d{4}$/.test(v)) {
        const [d, m, y] = v.split('/');
        const t = new Date(`${y}-${m}-${d}T00:00:00`).getTime();
        return isNaN(t) ? 0 : t;
      }

      const t = new Date(v).getTime();
      return isNaN(t) ? 0 : t;
    };

    return [...(Array.isArray(lista) ? lista : [])].sort((a, b) => {
      const diff = toTime(b) - toTime(a);
      if (diff !== 0) return diff;
      return Number((b as any)?.id ?? 0) - Number((a as any)?.id ?? 0);
    });
  }

  private addDays(date: Date, days: number) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
  }

  private toYMD(d?: string | Date | null): string {
    if (!d) return '';
    if (typeof d === 'string') {
      if (/^\d{4}-\d{2}-\d{2}/.test(d)) return d.slice(0,10);
      const asDate = new Date(d);
      if (!isNaN(asDate.getTime())) return asDate.toISOString().slice(0,10);
      if (/^\d{2}\/\d{2}\/\d{4}$/.test(d)) {
        const [dd,mm,yy] = d.split('/');
        return `${yy}-${mm}-${dd}`;
      }
      return '';
    }
    return new Date(d).toISOString().slice(0,10);
  }

  private parseDate(d?: string | Date | null): Date | null {
    const ymd = this.toYMD(d);
    if (!ymd) return null;
    const dt = new Date(`${ymd}T00:00:00`);
    return isNaN(dt.getTime()) ? null : dt;
  }

  private rangesOverlap(aStart: Date, aEnd: Date, bStart: Date, bEnd: Date): boolean {
    return aStart < bEnd && bStart < aEnd;
  }

  private calcularIndisponiveis(entradaDesejada: Date, saidaDesejada: Date): Set<string> {
    const bloqueados = new Set<string>();

    for (const h of this.hospedagensBase) {
      if (!this.isAtivo(h)) continue;

      const ent = this.parseDate(h.dataEntrada as any);
      if (!ent) continue;

      const diarias = Number(h.numeroDiarias ?? 1);
      const sai = this.parseDate(h.dataSaida as any) || this.addDays(ent, Math.max(diarias,1));
      const num = String(h.numeroQuarto ?? '').trim();
      if (!num) continue;

      if (this.rangesOverlap(entradaDesejada, saidaDesejada, ent, sai)) {
        bloqueados.add(num);
      }
    }
    return bloqueados;
  }

  private mapTipoQuarto(tipo?: string): string {
    const t = (tipo || '').toUpperCase();
    if (t.includes('TRIP')) return 'Triplo';
    if (t.includes('DUP') || t.includes('DOUBLE')) return 'Duplo';
    if (t.includes('IND') || t.includes('SING') || t.includes('SOLO')) return 'Individual';
    return '—';
  }

  rotuloQuarto(q: { numero: string; tipo?: string }): string {
    const tipo = this.mapTipoQuarto(q.tipo);
    return tipo === '—' ? `Quarto ${q.numero}` : `Quarto ${q.numero} — ${tipo}`;
  }

  private carregarQuartosDisponiveisComTipo() {
    const hoje = new Date();
    const diarias = Math.max(Number(this.novaHospedagem.numeroDiarias) || 1, 1);
    const saida = this.addDays(hoje, diarias);

    const indisponiveis = this.calcularIndisponiveis(hoje, saida);

    this.hospedagemService.getQuartosCatalogo().subscribe({
      next: (catalogo: QuartoCatalogo[]) => {
        const soLivres = (catalogo || []).filter(q => !indisponiveis.has(String(q.numero)));
        this.quartosDisponiveis = soLivres.sort((a,b) => Number(a.numero) - Number(b.numero));
      },
      error: () => {
        this.hospedagemService
          .getQuartosDisponiveis({ dataEntrada: hoje, dataSaida: saida })
          .subscribe({
            next: (q) => {
              const soLivres = (q || []).map(x => ({ numero: x.numero }));
              this.quartosDisponiveis = soLivres.sort((a,b) => Number(a.numero) - Number(b.numero));
            },
            error: () => this.toast.error('Falha ao carregar quartos disponíveis.')
          });
      }
    });
  }

  // ----------------- AUTOCOMPLETE DE E-MAIL -----------------

  onEmailHospedeInput(ev: Event) {
    const value = (ev.target as HTMLInputElement).value || '';
    this.novaHospedagem.email = value.trim();

    const atIndex = value.indexOf('@');
    if (atIndex < 0) {
      this.closeEmailHints();
      return;
    }

    const local = value.slice(0, atIndex).trim();
    const after = value.slice(atIndex + 1).toLowerCase();

    if (!local) {
      this.closeEmailHints();
      return;
    }

    const list = !after
      ? this.commonDomains
      : this.commonDomains.filter((d) => d.startsWith(after));

    this.emailCandidates = list;
    this.emailActiveIndex = 0;
    this.emailHintsOpen = list.length > 0;
  }

  onEmailHospedeKeydown(ev: KeyboardEvent) {
    if (!this.emailHintsOpen || !this.emailCandidates.length) return;

    switch (ev.key) {
      case 'ArrowDown':
        ev.preventDefault();
        this.emailActiveIndex =
          (this.emailActiveIndex + 1) % this.emailCandidates.length;
        break;
      case 'ArrowUp':
        ev.preventDefault();
        this.emailActiveIndex =
          (this.emailActiveIndex - 1 + this.emailCandidates.length) %
          this.emailCandidates.length;
        break;
      case 'Enter':
      case 'Tab':
        ev.preventDefault();
        this.applyEmailDomainHospede(this.emailCandidates[this.emailActiveIndex]);
        break;
      case 'Escape':
        this.closeEmailHints();
        break;
    }
  }

  onEmailHospedeBlur() {
    setTimeout(() => this.closeEmailHints(), 100);
  }

  applyEmailDomainHospede(domain: string) {
    const value = (this.novaHospedagem.email || '').toString();
    const atIndex = value.indexOf('@');
    if (atIndex < 0) return;

    const local = value.slice(0, atIndex).trim();
    this.novaHospedagem.email = `${local}@${domain}`;
    this.closeEmailHints();
  }

  private closeEmailHints() {
    this.emailHintsOpen = false;
    this.emailCandidates = [];
    this.emailActiveIndex = 0;
  }

  // ----------------- CARREGAR / FILTRAR LISTA -----------------

  buscarHospedagensIniciais() {
    this.estaBuscandoHospedagens = true; // INICIA CARREGAMENTO

    this.hospedagemService.listarHospedagens().subscribe({
      next: (hospedagens) => {
        this.estaBuscandoHospedagens = false; // FINALIZA CARREGAMENTO (sucesso)
        const lista = Array.isArray(hospedagens) ? hospedagens : [];
        this.hospedagensBase = this.ordenarPorMaisNovas(lista);
        this.aplicarTodosFiltros();
      },
      error: (err) => {
        this.estaBuscandoHospedagens = false; // FINALIZA CARREGAMENTO (erro)
        console.error('Erro ao carregar hospedagens:', err);
        this.toast.error('Falha ao carregar hospedagens.');
        this.hospedagensBase = [];
        this.hospedagens = [];
        this.atualizarPaginacao();
      }
    });
  }

  private aplicarTodosFiltros() {
    const termo = this.normalizeText(this.filtroNomeCpf);
    const termoDigits = this.onlyDigits(termo);
    const tipoFiltro = this.normalizeTipo(this.filtroTipo);
    const statusFiltro = this.normalizeStatus(this.filtroStatus);
    const ini = (this.filtroDataInicio || '').slice(0, 10);
    const fim = (this.filtroDataFim || '').slice(0, 10);

    let lista = [...this.hospedagensBase];

    if (termo) {
      lista = lista.filter((h) => {
        const nome = this.normalizeText(h?.nome ?? '');
        const cpfDigits = this.onlyDigits(String(h?.cpf ?? ''));
        const codigoRaw = String(
          h?.codigoHospedagem ??
          (h as any)?.codigo ??
          (h as any)?.id ??
          ''
        );
        const codigo = this.normalizeText(codigoRaw);
        const codigoDigits = this.onlyDigits(codigoRaw);
        const quarto = this.normalizeText(String(h?.numeroQuarto ?? ''));

        return (
          nome.includes(termo) ||
          (termoDigits && cpfDigits.includes(termoDigits)) ||
          codigo.includes(termo) ||
          (termoDigits && codigoDigits.includes(termoDigits)) ||
          quarto.includes(termo)
        );
      });
    }

    if (tipoFiltro) {
      lista = lista.filter(
        (h) => this.normalizeTipo((h as any)?.tipo || '') === tipoFiltro
      );
    }

    if (statusFiltro) {
      lista = lista.filter((h) =>
        this.isAtivo(h)
          ? statusFiltro === 'ATIVO'
          : this.isInativo(h)
            ? statusFiltro === 'INATIVO'
            : false
      );
    }

    if (ini || fim) {
      lista = lista.filter((h) => this.inDateRange((h as any)?.dataEntrada, ini, fim));
    }

    this.hospedagens = this.ordenarPorMaisNovas(lista);
    this.paginaAtual = 1;
    this.atualizarPaginacao();
  }

  aplicarFiltros() { this.aplicarTodosFiltros(); }

  onChangeBuscaTexto() {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.aplicarTodosFiltros(), 150);
  }

  filtrar() { this.aplicarTodosFiltros(); }

  filtrarPorPeriodo(dataInicio: string, dataFim: string) {
    this.filtroDataInicio = dataInicio;
    this.filtroDataFim = dataFim;
    this.aplicarTodosFiltros();
  }

  selecionarFiltroRapido(tipo: FiltroRapido): void {
    this.filtroRapido = tipo;
    const hoje = new Date();
    const fmt = (d: Date) => d.toISOString().slice(0, 10);

    if (tipo === 'HOJE') {
      this.filtroDataInicio = fmt(hoje);
      this.filtroDataFim = fmt(hoje);
    } else if (tipo === '7DIAS') {
      const d7 = new Date();
      d7.setDate(d7.getDate() - 6);
      this.filtroDataInicio = fmt(d7);
      this.filtroDataFim = fmt(hoje);
    } else if (tipo === 'MES') {
      const primeiro = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
      this.filtroDataInicio = fmt(primeiro);
      this.filtroDataFim = fmt(hoje);
    } else {
      this.filtroDataInicio = '';
      this.filtroDataFim = '';
    }
    this.aplicarTodosFiltros();
  }

  onChangeData(): void {
    this.filtroRapido = 'TODOS';
    this.aplicarTodosFiltros();
  }

  resetarFiltros() {
    this.filtroNomeCpf = '';
    this.filtroStatus = '';
    this.filtroTipo = '';
    this.filtroDataInicio = '';
    this.filtroDataFim = '';
    this.buscarHospedagensIniciais();
  }

  // ----------------- PAGINAÇÃO -----------------

  get hospedagensPaginadas() {
    if (!Array.isArray(this.hospedagens)) return [];
    const start = (this.paginaAtual - 1) * this.itensPorPagina;
    const end = start + this.itensPorPagina;
    return this.hospedagens.slice(start, end);
  }

  atualizarPaginacao() {
    this.totalPaginas = Math.ceil(this.hospedagens.length / this.itensPorPagina) || 1;
  }

  proximaPagina() { if (this.paginaAtual < this.totalPaginas) this.paginaAtual++; }
  paginaAnterior() { if (this.paginaAtual > 1) this.paginaAtual--; }

  // ----------------- FINALIZAR HOSPEDAGEM -----------------

  abrirModalFinalizar(hospedagem: HospedagemResponse) {
    this.hospedagemParaFinalizar = hospedagem;
    this.descricaoFinalizar = '';
    this.temErroDescricao = false;
    this.modalFinalizarAberto = true;
    this.modalScroll.lock();
  }

  fecharModalFinalizar() {
    this.modalFinalizarAberto = false;
    this.hospedagemParaFinalizar = null;
    this.descricaoFinalizar = '';
    this.temErroDescricao = false;
    this.modalScroll.unlock();
  }

  abrirModalSucessoCheckout(h?: HospedagemResponse | null) {
    // se vier do backend usa o retorno, senão usa a que estava sendo finalizada
    this.hospedagemFinalizada = h ?? this.hospedagemParaFinalizar;
    this.modalSucessoCheckoutAberto = true;
    this.modalScroll.lock();
  }

  fecharModalSucessoCheckout() {
    this.modalSucessoCheckoutAberto = false;
    this.hospedagemFinalizada = null;
    this.modalScroll.unlock();
  }

  validarDescricao() {
    this.temErroDescricao = !this.descricaoFinalizar.trim();
  }

  confirmarFinalizarHospedagem() {
    this.validarDescricao();
    if (this.temErroDescricao) {
      this.toast.warning('Preencha o motivo da finalização.');
      return;
    }

    this.estaCarregando = true; // Inicia o carregamento
    
    this.hospedagemService
      .realizarCheckout({
        numeroQuarto: String(this.hospedagemParaFinalizar?.numeroQuarto),
        descricao: this.descricaoFinalizar
      })
      .subscribe({
        next: (res: any) => {
          this.estaCarregando = false; // Finaliza o carregamento (sucesso)

          const finalizada: HospedagemResponse | null =
            (res && res.id) ? (res as HospedagemResponse) : (this.hospedagemParaFinalizar as HospedagemResponse | null);

          this.fecharModalFinalizar();
          this.buscarHospedagensIniciais();
          this.abrirModalSucessoCheckout(finalizada);

          this.toast.success('Hospedagem finalizada com sucesso!');
        },
        error: (error) => {
          this.estaCarregando = false; // Finaliza o carregamento (erro)
          this.mensagemErro =
            error?.error?.mensagem || 'Erro ao finalizar hospedagem.';
          this.toast.error(this.mensagemErro || 'Erro ao finalizar hospedagem.');
        }
      });
  }

  // ----------------- MODAL SUCESSO CHECKIN (CRIAÇÃO) -----------------

  abrirModalSucessoCheckin(h?: HospedagemResponse | null) {
    this.hospedagemCriada = h ?? null;
    this.modalSucessoCheckinAberto = true;
    this.modalScroll.lock();
  }

  fecharModalSucessoCheckin() {
    this.modalSucessoCheckinAberto = false;
    this.hospedagemCriada = null;
    this.modalScroll.unlock();
  }

  // ----------------- COMPROVANTE / PDF -----------------

  baixarComprovantePDF(hospedagem: HospedagemResponse) {
    // Adiciona o controle de carregamento
    if (!hospedagem || this.estaGerandoComprovante) return;

    this.estaGerandoComprovante = true; // Inicia o carregamento
    // Marca a hospedagem que está sendo processada (para mostrar spinner só no botão correspondente)
    this.gerandoComprovanteParaId = (hospedagem as any)?.id ?? (hospedagem as any)?.codigoHospedagem ?? (hospedagem as any)?.numeroQuarto ?? (hospedagem as any)?.nome ?? null;

    const email = (hospedagem as any).email || '-';

    const antigo = document.getElementById('comprovante-pdf-temp');
    if (antigo) antigo.remove();

    const agora = new Date();
    const dataFormatada = agora.toLocaleDateString('pt-BR');
    const horaFormatada = agora.toLocaleTimeString('pt-BR');

    const codigo = hospedagem.codigoHospedagem || '-';
    const criadoPor = hospedagem.criadoPor || '-';

    const element = document.createElement('div');
    element.id = 'comprovante-pdf-temp';
    element.style.position = 'fixed';
    element.style.top = '-9999px';
    element.style.left = '0';

    element.innerHTML = `
<div style="font-family:'Segoe UI',Arial,sans-serif;width:420px;border-radius:18px;box-shadow:0 4px 32px #0002;padding:32px 38px 18px 38px;background:#fff;color:#233642;position:relative;overflow:hidden;">
  <div style="text-align:center;margin-top:0;">
    <img src="assets/logo.png" alt="Logo" style="height:108px;max-width:200px;display:block;margin:0 auto 7px auto;" />
    <div style="font-size:15px;color:#A0A0A0;margin-bottom:7px; margin-top:1px;">CNPJ: 12.886.443/0001-00</div>
  </div>
  <div style="font-size:17px;color:#233642;text-align:center;font-weight:600;margin-bottom:8px;letter-spacing:0.5px;margin-top:2px;">COMPROVANTE DE HOSPEDAGEM</div>
  <div style="border:1.5px solid #e8e8e8;border-radius:12px;padding:13px 16px 12px 16px;background:#FAFAFA;margin-bottom:10px;margin-top:10px;">
    <div style="font-size:16px;margin-bottom:8px;"><b>Hóspede:</b> ${hospedagem.nome ?? '-'}</div>
    <div style="font-size:16px;margin-bottom:8px;"><b>CPF:</b> ${hospedagem.cpf || '-'}</div>
    <div style="font-size:16px;margin-bottom:8px;"><b>E-mail:</b> ${email}</div>
    <div style="font-size:16px;margin-bottom:8px;"><b>Quarto:</b> ${(hospedagem.numeroQuarto || '-')}</div>
    <div style="font-size:16px;margin-bottom:8px;"><b>Entrada:</b> ${this.formatarData(hospedagem.dataEntrada)}</div>
    <div style="font-size:16px;margin-bottom:8px;"><b>Saída:</b> ${hospedagem.dataSaida ? this.formatarData(hospedagem.dataSaida) : '-'}</div>
    <div style="font-size:16px;margin-bottom:8px;"><b>Quantidade de Diárias:</b> ${hospedagem.numeroDiarias ?? '-'}</div>
    <div style="font-size:16px;margin-bottom:8px;"><b>Valor Diária:</b> R$ ${(Number(hospedagem.valorDiaria ?? 0)).toFixed(2).replace('.', ',')}</div>
    <div style="font-size:15px; margin-bottom:9px;"><b>VALOR TOTAL:</b> R$ ${(Number(hospedagem.valorTotal ?? 0)).toFixed(2).replace('.', ',')}</div>
    <div style="font-size:16px;margin-bottom:6px;"><b>Pagamento:</b> ${hospedagem.formaPagamento ?? '-'}</div>
    <div style="font-size:16px;"><b>Observações:</b> ${hospedagem.observacoes || '-'}</div>
  </div>

  <div style="font-size:13px;text-align:center;color:#A0A0A0;margin-top:8px;">
    Emitido em ${dataFormatada} ${horaFormatada}
  </div>

  <div style="font-size:13px;text-align:center;color:#A0A0A0;margin-top:4px;">
    Código: ${codigo} | Criado por: ${criadoPor}
  </div>
</div>`;

    document.body.appendChild(element);

    setTimeout(() => {
      html2canvas(element, { scale: 2 })
        .then((canvas) => {
          const imgData = canvas.toDataURL('image/png');
          const pdf = new jsPDF({ orientation: 'portrait', unit: 'pt', format: [440, 650] });
          pdf.addImage(imgData, 'PNG', 8, 8, 420, 630, '', 'FAST');
          pdf.save(`Comprovante_${hospedagem.nome || 'hospedagem'}.pdf`);
          this.toast.success('Comprovante gerado.');
        })
        .catch(() => {
          this.toast.error('Falha ao gerar o comprovante.');
        })
        .finally(() => {
          this.estaGerandoComprovante = false; // Finaliza o carregamento (sucesso ou erro)
          this.gerandoComprovanteParaId = null;
          element.remove();
        });
    }, 300);
  }

  // ----------------- NOVA / EDITAR HOSPEDAGEM -----------------

  abrirModalNovaHospedagem() {
    this.editandoHospedagem = false;
    this.hospedagemParaEditar = null;

    this.novaHospedagem = {
      nome: '',
      cpf: '',
      email: '',
      numeroQuarto: '',
      numeroDiarias: '',
      valorDiaria: '',
      formaPagamento: '',
      observacoes: '',
      tipo: ''
    };

    this.valorDiariaFormatado = '';
    this.mensagemErro = '';

    this.carregarQuartosDisponiveisComTipo();
    this.modalNovaHospedagemAberto = true;
    this.modalScroll.lock();
  }

  abrirModalEditar(h: HospedagemResponse) {
    this.editandoHospedagem = true;
    this.hospedagemParaEditar = h;

    const quartoAtual = this._s(h?.numeroQuarto);
    const email = (h as any).email || '';

    this.novaHospedagem = {
      nome: h?.nome || '',
      cpf: (h?.cpf as any) || '',
      email,
      numeroQuarto: quartoAtual,
      numeroDiarias: (h?.numeroDiarias as any) || '',
      valorDiaria: (h?.valorDiaria as any) || '',
      formaPagamento: h?.formaPagamento || '',
      observacoes: h?.observacoes || '',
      tipo: (this.normalizeTipo((h as any)?.tipo) as any) || 'COMUM'
    };

    this.valorDiariaFormatado = h?.valorDiaria
      ? Number(h.valorDiaria).toLocaleString('pt-BR', { minimumFractionDigits: 2 })
      : '';
    this.mensagemErro = '';

    this.carregarQuartosDisponiveisComTipo();
    this.modalNovaHospedagemAberto = true;
    this.modalScroll.lock();
  }

  fecharModalNovaHospedagem() {
    this.modalNovaHospedagemAberto = false;
    this.modalScroll.unlock();
  }

  confirmarNovaHospedagem() {
    this.mensagemErro = '';

    const numeroQuartoStr = this._s(this.novaHospedagem.numeroQuarto);
    const numeroDiariasNum = Number(this.novaHospedagem.numeroDiarias);
    const valorDiariaNum = Number(this.novaHospedagem.valorDiaria);

    const baseInvalida =
      !this._s(this.novaHospedagem.nome) ||
      !numeroQuartoStr ||
      !this.novaHospedagem.numeroDiarias ||
      isNaN(numeroDiariasNum) ||
      numeroDiariasNum <= 0 ||
      !this._s(this.novaHospedagem.formaPagamento) ||
      isNaN(valorDiariaNum) ||
      valorDiariaNum <= 0;

    // --------- CRIAÇÃO (CHECKIN) ---------
    if (!this.editandoHospedagem) {
      if (baseInvalida || !this._s(this.novaHospedagem.tipo)) {
        this.mensagemErro = 'Por favor, preencha todos os campos obrigatórios corretamente.';
        this.toast.warning(this.mensagemErro);
        return;
      }

      this.estaCarregando = true; // Inicia o carregamento (criação)

      const payloadCriacao: any = {
        nome: this._s(this.novaHospedagem.nome),
        cpf: this.novaHospedagem.cpf || null,
        email: this._s(this.novaHospedagem.email) || null,
        numeroQuarto: numeroQuartoStr,
        numeroDiarias: numeroDiariasNum,
        valorDiaria: valorDiariaNum,
        formaPagamento: this._s(this.novaHospedagem.formaPagamento),
        observacoes: this._s(this.novaHospedagem.observacoes),
        tipo: this.novaHospedagem.tipo as 'COMUM' | 'PREFEITURA' | 'CORPORATIVO'
      };

      this.hospedagemService.realizarCheckin(payloadCriacao).subscribe({
        next: (res: any) => {
          this.estaCarregando = false; // Finaliza o carregamento (sucesso)

          this.fecharModalNovaHospedagem();
          this.resetarFiltros();
          this.buscarHospedagensIniciais();

          const criada = (res && res.id)
            ? (res as HospedagemResponse)
            : null;

          this.abrirModalSucessoCheckin(criada);
          this.toast.success('Hospedagem criada com sucesso!');
        },
        error: (err) => {
          this.estaCarregando = false; // Finaliza o carregamento (erro)
          const msg =
            err?.error?.mensagem
            || err?.error?.message
            || (typeof err?.error === 'string' ? err.error : '')
            || err?.message
            || 'Erro ao criar nova hospedagem.';
          this.mensagemErro = msg;
          this.toast.error(msg);
        }
      });

      return;
    }

    // --------- EDIÇÃO ---------
    if (baseInvalida) {
      this.mensagemErro = 'Por favor, preencha os campos obrigatórios corretamente.';
      this.toast.warning(this.mensagemErro);
      return;
    }

    const diff = this._buildEditDiff(
      this.hospedagemParaEditar!,
      {
        numeroQuarto: numeroQuartoStr,
        numeroDiarias: numeroDiariasNum,
        formaPagamento: this.novaHospedagem.formaPagamento,
        observacoes: this.novaHospedagem.observacoes
      }
    );

    if (Object.keys(diff).length === 0) {
      this.mensagemErro = 'Nenhuma alteração detectada.';
      this.toast.info(this.mensagemErro);
      return;
    }

    this.estaCarregando = true; // Inicia o carregamento (edição)
    
    this.hospedagemService.editarHospedagem(this.hospedagemParaEditar!.id, diff).subscribe({
      next: () => {
        this.estaCarregando = false; // Finaliza o carregamento (sucesso)

        this.fecharModalNovaHospedagem();
        this.buscarHospedagensIniciais();
        this.hospedagemParaEditar = null;
        this.toast.success('Hospedagem atualizada com sucesso!');
      },
      error: (err) => {
        this.estaCarregando = false; // Finaliza o carregamento (erro)

        this.mensagemErro = err?.error?.mensagem || 'Erro ao editar hospedagem. Tente novamente.';
        this.toast.error(this.mensagemErro || 'Erro ao editar hospedagem.');
      }
    });
  }

  private _buildEditDiff(
    original: HospedagemResponse,
    form: Partial<EditarHospedagemDTO>
  ): Partial<EditarHospedagemDTO> {
    const orig = {
      numeroQuarto: this._s(original?.numeroQuarto),
      numeroDiarias: Number(original?.numeroDiarias ?? 0),
      formaPagamento: this._s(original?.formaPagamento),
      observacoes: this._s(original?.observacoes)
    };

    const novo = {
      numeroQuarto: this._s(form?.numeroQuarto),
      numeroDiarias: Number(form?.numeroDiarias ?? 0),
      formaPagamento: this._s(form?.formaPagamento),
      observacoes: this._s(form?.observacoes)
    };

    const diff: any = {};
    (Object.keys(novo) as (keyof typeof novo)[]).forEach((k) => {
      if (novo[k] !== (orig as any)[k]) diff[k] = novo[k];
    });
    return diff;
  }

  // ----------------- FORMATADORES -----------------

  onValorDiariaInput(event: Event) {
    const input = event.target as HTMLInputElement;
    let valor = input.value.replace(/[^\d]/g, '');
    if (valor.length === 0) {
      this.valorDiariaFormatado = '';
      this.novaHospedagem.valorDiaria = '';
      return;
    }
    const valorNumerico = (parseInt(valor, 10) / 100).toFixed(2);
    this.valorDiariaFormatado = Number(valorNumerico).toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
    this.novaHospedagem.valorDiaria = Number(valorNumerico);
  }

  setNumeroDiariasPreset(n: number) {
    this.novaHospedagem.numeroDiarias = n;
    this.carregarQuartosDisponiveisComTipo();
  }

  onChangeNumeroDiariasManual() {
    this.carregarQuartosDisponiveisComTipo();
  }

  setValorDiariaPreset(v: number) {
    this.novaHospedagem.valorDiaria = v;
    try {
      this.valorDiariaFormatado = Number(v).toLocaleString('pt-BR', {
        minimumFractionDigits: 2
      });
    } catch {
      this.valorDiariaFormatado = String(v);
    }
  }

  permitirSomenteNumeros(event: KeyboardEvent) {
    if (
      (event.key >= '0' && event.key <= '9') ||
      ['Backspace','Delete','ArrowLeft','ArrowRight','Tab'].includes(event.key)
    ) return;
    event.preventDefault();
  }

  onCpfInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const valor = input.value.replace(/\D/g, '').slice(0, 11);
    let cpfFormatado = valor;

    if (valor.length > 9)
      cpfFormatado = valor.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
    else if (valor.length > 6)
      cpfFormatado = valor.replace(/(\d{3})(\d{3})(\d{0,3})/, '$1.$2.$3');
    else if (valor.length > 3)
      cpfFormatado = valor.replace(/(\d{3})(\d{0,3})/, '$1.$2');

    input.value = cpfFormatado;
    this.novaHospedagem.cpf = valor;
  }

  formatarCpf(valor: string) {
    if (!valor) return '';
    valor = valor.replace(/\D/g, '').slice(0, 11);
    if (valor.length === 11) return valor.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
    if (valor.length > 6)   return valor.replace(/(\d{3})(\d{3})(\d{0,3})/, '$1.$2.$3');
    if (valor.length > 3)   return valor.replace(/(\d{3})(\d{0,3})/, '$1.$2');
    return valor;
  }

  formatarData(data: string | Date | null | undefined) {
    if (!data) return '-';
    if (typeof data === 'string' && /^\d{4}-\d{2}-\d{2}/.test(data)) {
      const [ano, mes, dia] = data.split('-');
      return `${dia}/${mes}/${ano}`;
    }
    if (typeof data === 'string' && data.includes('T')) {
      const [d] = data.split('T');
      const [ano, mes, dia] = d.split('-');
      return `${dia}/${mes}/${ano}`;
    }
    const d = new Date(data);
    return d.toLocaleDateString('pt-BR');
  }

  rotuloTipo(valor: string): string {
    if (!valor) return 'Normal';
    switch (String(valor).toUpperCase()) {
      case 'PREFEITURA':  return 'Prefeitura';
      case 'CORPORATIVO': return 'Corporativo';
      case 'COMUM':       return 'Normal';
      default:            return 'Normal';
    }
  }

  formatarDataHora(data: string | Date | null | undefined): string {
    if (!data) return '-';
    try {
      if (typeof data === 'string') {
        if (data.includes('T')) {
          const d = new Date(data);
          return d.toLocaleString('pt-BR', { hour12: false });
        }
        if (/^\d{4}-\d{2}-\d{2}$/.test(data)) {
          const d = new Date(data + 'T00:00:00');
          return d.toLocaleString('pt-BR', { hour12: false });
        }
      }
      const d = new Date(data);
      return d.toLocaleString('pt-BR', { hour12: false });
    } catch {
      return '-';
    }
  }

  // ----------------- DETALHES HOSPEDAGEM -----------------

  abrirDetalhes(h: HospedagemResponse) {
    this.hospedagemSelecionada = h;
    this.showDetalhes = true;
    this.modalScroll.lock();
  }

  fecharDetalhes() {
    this.showDetalhes = false;
    this.hospedagemSelecionada = null;
    this.modalScroll.unlock();
  }

  // ----------------- EXCLUIR HOSPEDAGEM -----------------

  abrirModalExcluir(h: HospedagemResponse) {
    this.hospedagemParaExcluir = h;
    this.modalExcluirAberto = true;
    this.modalScroll.lock();
  }

  fecharModalExcluir() {
    this.modalExcluirAberto = false;
    this.hospedagemParaExcluir = null;
    this.modalScroll.unlock();
  }

  confirmarExcluirHospedagem() {
    if (!this.hospedagemParaExcluir) return;
    
    this.estaCarregando = true; // Inicia o carregamento

    this.hospedagemService.excluirHospedagem(this.hospedagemParaExcluir.id).subscribe({
      next: () => {
        this.estaCarregando = false; // Finaliza o carregamento (sucesso)

        this.toast.success('Hospedagem excluída com sucesso!');
        this.fecharModalExcluir();
        this.buscarHospedagensIniciais();
      },
      error: (err) => {
        this.estaCarregando = false; // Finaliza o carregamento (erro)

        const msg = err?.error?.mensagem || 'Erro ao excluir hospedagem.';
        this.toast.error(msg);
      }
    });
  }

}