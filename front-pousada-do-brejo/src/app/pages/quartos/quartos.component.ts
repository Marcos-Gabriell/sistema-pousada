import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { QuartosService, Quarto } from './quartos.service';
import { ToastService } from '../../toast/toast.service';
import { ModalScrollService } from '../../services/modal-scroll.service';

type Modal = 'novo' | 'editar' | 'detalhes' | 'excluir' | 'sucesso' | null;

@Component({
  selector: 'app-quartos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './quartos.component.html',
  styleUrls: ['./quartos.component.css']
})
export class QuartosComponent implements OnInit {

  quartos: Quarto[] = [];
  carregandoLista = false;        
  estaCarregandoAcao = false;       

  filtro = { termo: '', status: '', tipo: '' };
  private searchTimer: any = null;

  tipos = ['INDIVIDUAL', 'DUPLO', 'TRIPLO'];
  status = ['DISPONIVEL', 'OCUPADO', 'MANUTENCAO'];

  modalAbertura: Modal = null;
  selectedQuarto: Quarto | null = null;
  deleting = false;

  // modal de sucesso após criar
  sucesso: {
    id?: number;
    codigo?: number;
    numero?: string;
    nome?: string;
    tipo?: string;
    valorDiaria?: number;
    status?: string;
    criadoPorNome?: string;
    criadoEm?: string | Date;
  } | null = null;

  model: {
    id?: number;
    numero: string;
    nome: string;
    tipo: string;
    status?: string;
    valorDiaria: string;
    motivo?: string; 
  } = this.vazio();

  page = 1;
  pageSize = 5;

  constructor(
    private quartosService: QuartosService,
    private toast: ToastService
    , private modalScroll: ModalScrollService
  ) {}

  ngOnInit(): void {
    this.listar();
  }

  private vazio() {
    return {
      numero: '',
      nome: '',
      tipo: this.tipos[0],
      status: 'DISPONIVEL',
      valorDiaria: '',
      motivo: ''
    };
  }

  private showApiError(e: any, fallback: string) {
    const raw = e?.error ?? e;
    const pick = (o: any) => o?.mensagem || o?.message || o?.error || o?.statusText || '';
    let texto = '';
    if (typeof raw === 'string') {
      try { texto = pick(JSON.parse(raw)) || raw; } catch { texto = raw; }
    } else {
      texto = pick(raw);
    }
    this.toast.error((texto || fallback).toString());
  }

  private valorDiariaToNumber(): number {
    const raw = (this.model.valorDiaria || '').trim();
    if (!raw) return NaN;
    const n = parseFloat(raw.replace(/\./g, '').replace(',', '.'));
    return Number.isFinite(n) ? n : NaN;
  }

  private existeNumeroOuNome(numero: string, nome: string, idAtual?: number): boolean {
    const num = (numero || '').trim();
    const nomeLower = (nome || '').trim().toLowerCase();
    return this.quartos.some(q => {
      const igualNum = String(q.numero ?? '').trim() === num;
      const igualNome = String(q.nome ?? '').trim().toLowerCase() === nomeLower;
      const outro = !idAtual || q.id !== idAtual;
      return (igualNum || igualNome) && outro;
    });
  }

  private houveAlteracao(
    orig: Quarto,
    novo: { numero: string; nome: string; tipo: string; status?: string; valorDiaria: number; }
  ): boolean {
    const vOrig = Number(orig.valorDiaria ?? 0);
    return (
      String(orig.numero ?? '').trim() !== String(novo.numero ?? '').trim() ||
      String(orig.nome ?? '').trim()   !== String(novo.nome ?? '').trim()   ||
      String(orig.tipo ?? '').trim()   !== String(novo.tipo ?? '').trim()   ||
      String(orig.status ?? '').trim() !== String(novo.status ?? '').trim() ||
      (Number.isFinite(vOrig) && Number.isFinite(novo.valorDiaria) && vOrig !== novo.valorDiaria)
    );
  }

  onNumeroInput(ev: Event) {
    const el = ev.target as HTMLInputElement;
    const only = (el.value || '').replace(/\D/g, '').slice(0, 3);
    el.value = only;
    this.model.numero = only;
  }

  apenasNumerosKeydown(ev: KeyboardEvent) {
    const allow = ['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab'];
    if (allow.includes(ev.key)) return;
    if (!/^\d$/.test(ev.key)) ev.preventDefault();
  }

  onNumeroPaste(ev: ClipboardEvent) {
    ev.preventDefault();
    const text = ev.clipboardData?.getData('text') || '';
    const only = text.replace(/\D/g, '').slice(0, 3);
    (ev.target as HTMLInputElement).value = only;
    this.model.numero = only;
  }

  onValorInput(ev: Event) {
    const el = ev.target as HTMLInputElement;
    const digits = (el.value || '').replace(/[^\d]/g, '').slice(0, 7);
    if (!digits) {
      this.model.valorDiaria = '';
      el.value = '';
      return;
    }
    const num = (parseInt(digits, 10) / 100).toFixed(2);
    const masked = num.replace('.', ',');
    el.value = masked;
    this.model.valorDiaria = masked;
  }

  apenasNumeroVirgulaKeydown(ev: KeyboardEvent) {
    const allow = ['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab'];
    if (allow.includes(ev.key)) return;
    if (ev.key === ',') {
      if ((ev.target as HTMLInputElement).value.includes(',')) ev.preventDefault();
      return;
    }
    if (!/^\d$/.test(ev.key)) ev.preventDefault();
  }

  onValorPaste(ev: ClipboardEvent) {
    ev.preventDefault();
    const text = ev.clipboardData?.getData('text') || '';
    const digits = text.replace(/[^\d]/g, '').slice(0, 7);
    if (!digits) return;
    const num = (parseInt(digits, 10) / 100).toFixed(2).replace('.', ',');
    (ev.target as HTMLInputElement).value = num;
    this.model.valorDiaria = num;
  }

  onValorBlur() {
    const v = (this.model.valorDiaria || '').trim();
    if (!v) return;
    const n = this.valorDiariaToNumber();
    if (!Number.isFinite(n) || n <= 0) {
      this.model.valorDiaria = '';
      this.toast.warning('Informe um valor de diária válido.');
      return;
    }
    this.model.valorDiaria = n.toFixed(2).replace('.', ',');
  }

  // === filtros/paginação ===
  onBuscarChange() {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.buscar(), 250);
  }

  limparFiltros() {
    this.filtro = { termo: '', status: '', tipo: '' };
    this.page = 1;
    this.buscar();
  }

  listar() {
    this.carregandoLista = true;
    this.quartosService.listar().subscribe({
      next: (qs) => {
        this.quartos = qs || [];
        this.carregandoLista = false;
      },
      error: (e) => {
        this.carregandoLista = false;
        this.showApiError(e, 'Falha ao carregar quartos.');
      }
    });
  }

  buscar() {
    this.carregandoLista = true;
    const params: any = {};
    if (this.filtro.termo) params.termo = this.filtro.termo;
    if (this.filtro.status) params.status = this.filtro.status;
    if (this.filtro.tipo) params.tipo = this.filtro.tipo;

    this.quartosService.buscar(params).subscribe({
      next: (qs) => {
        this.quartos = qs || [];
        this.page = 1;
        this.carregandoLista = false;
      },
      error: (e) => {
        this.carregandoLista = false;
        this.showApiError(e, 'Falha na busca de quartos.');
      }
    });
  }

  // === ações ===
  abrirNovoQuarto() {
    this.model = this.vazio();
    this.selectedQuarto = null;
    this.modalAbertura = 'novo';
    this.modalScroll.lock();
  }

  editar(q: Quarto) {
    this.selectedQuarto = { ...q };
    const valorStr = Number(q?.valorDiaria || 0).toFixed(2).replace('.', ',');
    this.model = {
      id: q.id,
      numero: String(q.numero ?? '').trim(),
      nome: String(q.nome ?? '').trim(),
      tipo: String(q.tipo ?? '').trim(),
      status: String(q.status ?? '').trim() || 'DISPONIVEL',
      valorDiaria: valorStr,
      motivo: q.descricao || ''
    };
    this.modalAbertura = 'editar';
    this.modalScroll.lock();
  }

  detalhes(q: Quarto) {
    this.selectedQuarto = { ...q };
    this.modalAbertura = 'detalhes';
    this.modalScroll.lock();
  }

  abrirModalExcluir(q: Quarto) {
    this.selectedQuarto = { ...q };
    this.deleting = false;
    this.modalAbertura = 'excluir';
    this.modalScroll.lock();
  }

  fecharModal() {
    this.modalAbertura = null;
    this.selectedQuarto = null;
    this.sucesso = null;
    this.model = this.vazio();
    this.deleting = false;
    this.estaCarregandoAcao = false; // reset de segurança
    this.modalScroll.unlock();
  }

  private validarForm(isEdicao: boolean): boolean {
    const numero = Number(this.model.numero);
    const nome = (this.model.nome || '').trim();
    const tipo = (this.model.tipo || '').trim();
    const status = (this.model.status || '').trim();
    const valor = this.valorDiariaToNumber();

    if (!numero || numero < 1 || numero > 999) {
      this.toast.warning('Informe um número entre 1 e 999.');
      return false;
    }
    if (!nome || nome.length > 20) {
      this.toast.warning('Informe um nome/identificação (máx. 20).');
      return false;
    }
    if (!tipo) {
      this.toast.warning('Selecione o tipo do quarto.');
      return false;
    }
    if (isEdicao && !status) {
      this.toast.warning('Selecione o status.');
      return false;
    }
    if (!Number.isFinite(valor) || valor <= 0 || valor > 10000) {
      this.toast.warning('Informe um valor de diária válido.');
      return false;
    }
    return true;
  }

  criarQuarto() {
    if (!this.validarForm(false)) return;

    if (this.existeNumeroOuNome(this.model.numero, this.model.nome)) {
      this.toast.error('Já existe um quarto com este número ou nome.');
      return;
    }

    this.estaCarregandoAcao = true;

    const payload: Partial<Quarto> = {
      numero: this.model.numero.trim(),
      nome: this.model.nome.trim(),
      tipo: this.model.tipo as any,
      status: 'DISPONIVEL',
      descricao: (this.model.motivo || '').trim(),
      valorDiaria: this.valorDiariaToNumber()
    };

    this.quartosService.criar(payload as Quarto).subscribe({
      next: (q: Quarto) => {
        this.estaCarregandoAcao = false;

        this.sucesso = {
          id: q.id,
          codigo: q.codigo,
          numero: q.numero,
          nome: q.nome,
          tipo: q.tipo,
          valorDiaria: q.valorDiaria,
          status: q.status,
          criadoPorNome: (q as any).criadoPorNome,
          criadoEm: (q as any).criadoEm
        };
        this.modalAbertura = 'sucesso';
        this.listar(); 
      },
      error: (e) => {
        this.estaCarregandoAcao = false;
        this.showApiError(e, 'Erro ao criar quarto.');
      }
    });
  }

  atualizarQuarto() {
    if (!this.selectedQuarto?.id) return;
    if (!this.validarForm(true)) return;

    const novoValor = this.valorDiariaToNumber();
    const mudouNum  = this.model.numero.trim() !== String(this.selectedQuarto.numero ?? '').trim();
    const mudouNome = this.model.nome.trim()   !== String(this.selectedQuarto.nome ?? '').trim();

    if ((mudouNum || mudouNome) &&
      this.existeNumeroOuNome(this.model.numero, this.model.nome, this.selectedQuarto.id)) {
      this.toast.error('Já existe um quarto com este número ou nome.');
      return;
    }

    const payload: Partial<Quarto> = {
      numero: this.model.numero.trim(),
      nome: this.model.nome.trim(),
      tipo: this.model.tipo as any,
      status: this.model.status as any,
      descricao: (this.model.motivo || '').trim(),
      valorDiaria: novoValor
    };

    if (!this.houveAlteracao(this.selectedQuarto, payload as any)) {
      this.toast.info('Nenhuma alteração detectada.');
      return;
    }

    this.estaCarregandoAcao = true;

    this.quartosService.editar(this.selectedQuarto.id!, payload as Quarto).subscribe({
      next: () => {
        this.estaCarregandoAcao = false;

        this.toast.success('Quarto atualizado com sucesso!');
        this.fecharModal();
        this.listar();
      },
      error: (e) => {
        this.estaCarregandoAcao = false;
        this.showApiError(e, 'Erro ao atualizar quarto.');
      }
    });
  }

  excluirQuartoConfirmado() {
    if (!this.selectedQuarto?.id || this.deleting) return;

    this.estaCarregandoAcao = true;
    this.deleting = true;

    this.quartosService.excluir(this.selectedQuarto.id).subscribe({
      next: () => {
        this.toast.success('Quarto excluído com sucesso!');
        this.deleting = false;
        this.estaCarregandoAcao = false;
        this.fecharModal();
        this.listar();
      },
      error: (e) => {
        this.deleting = false;
        this.estaCarregandoAcao = false;
        this.showApiError(e, 'Erro ao excluir quarto.');
      }
    });
  }

  get quartosPaginados(): Quarto[] {
    const start = (this.page - 1) * this.pageSize;
    return this.quartos.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.quartos.length / this.pageSize));
  }

  paginaAnterior() {
    if (this.page > 1) this.page--;
  }

  proximaPagina() {
    if (this.page < this.totalPages) this.page++;
  }

  get estaSincronizandoBack(): boolean {
    return this.carregandoLista || this.estaCarregandoAcao;
  }
}
