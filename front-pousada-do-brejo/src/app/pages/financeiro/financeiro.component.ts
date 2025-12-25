import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import {
  FinanceiroService,
  Lancamento,
  TipoLancamento,
  OrigemLancamento,
  CriarLancamentoPayload,
  AtualizarLancamentoPayload,
} from './financeiro.service';

import { ToastService } from '../../toast/toast.service';
import { AuthService } from '../../services/login/auth.service';
import { ModalScrollService } from '../../services/modal-scroll.service';

type ModalKind = 'novo' | 'editar' | 'cancelar' | 'detalhes' | 'sucesso' | null;
type FiltroRapido = 'HOJE' | '7DIAS' | 'MES' | 'TODOS';

interface CurrentUser {
  id: number;
  nome: string;
  role: 'ADMIN' | 'DEV' | 'GERENTE' | string;
}

@Component({
  selector: 'app-financeiro',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './financeiro.component.html',
  styleUrls: ['./financeiro.component.css'],
})
export class FinanceiroComponent implements OnInit {
  all: Lancamento[] = [];
  base: Lancamento[] = [];
  view: Lancamento[] = [];
  totalAtual = 0;

  carregandoLista = false;
  estaCarregandoAcao = false;


  busca = '';
  filtroRapido: FiltroRapido = 'TODOS';
  dataInicio = '';
  dataFim = '';
  filtroTipo: TipoLancamento | 'TODOS' = 'TODOS';
  filtroOrigem: OrigemLancamento | 'TODAS' = 'TODAS';

  page = 1;
  pageSize = 5;

  modal: ModalKind = null;
  selected: Lancamento | null = null;
  editSnapshot: Lancamento | null = null;
  deleting = false;
  ultimoCriado: Lancamento | null = null;

  form = {
    id: undefined as number | undefined,
    tipo: 'SAIDA' as TipoLancamento,
    origem: 'MANUAL' as OrigemLancamento,
    descricao: '',
    valorStr: '',
    formaPagamento: '',
  };

  valorFormatado = '';
  downloadingComprovante = false;

  currentUser: CurrentUser | null = null;
  isAdmin = false;
  isDev = false;
  isGerente = false;

  constructor(
    private financeiro: FinanceiroService,
    private toast: ToastService,
    private auth: AuthService
    , private modalScroll: ModalScrollService
  ) {}

  ngOnInit(): void {
    this.carregarUsuario();
    this.selecionarFiltroRapido('TODOS');
  }

  private carregarUsuario(): void {
    try {
      const authUser = this.auth.getCurrentUser();
      const primaryRole = this.auth.getPrimaryRole();

      this.currentUser = {
        id: (authUser?.id as number) || 0,
        nome: authUser?.nome || 'Usuário',
        role: primaryRole,
      };

      this.isAdmin = this.currentUser.role === 'ADMIN';
      this.isDev = this.currentUser.role === 'DEV';
      this.isGerente = this.currentUser.role === 'GERENTE';
    } catch {
      this.currentUser = { id: 0, nome: 'Usuário (fallback)', role: '' };
      this.isGerente = false;
      this.isAdmin = false;
      this.isDev = false;
    }
  }

  podeCriar(): boolean { return this.isAdmin || this.isDev || this.isGerente; }
  podeEditar(): boolean { return this.isAdmin || this.isDev; }
  podeCancelar(): boolean { return this.isAdmin || this.isDev; }
  podeVerDetalhes(): boolean { return true; }

  listar(): void {
    this.carregandoLista = true;

    this.financeiro
      .listar({
        tipo: this.filtroTipo,
        origem: this.filtroOrigem,
        inicio: this.dataInicio || undefined,
        fim: this.dataFim || undefined,
      })
      .subscribe({
        next: (lst) => {
          this.carregandoLista = false;
          this.all = Array.isArray(lst) ? lst : [];
          this.all.sort((a, b) => {
            const dateA = a.data ? new Date(a.data).getTime() : 0;
            const dateB = b.data ? new Date(b.data).getTime() : 0;
            return dateB - dateA;
          });
          this.base = [...this.all];
          this.aplicarFiltroLocal();
        },
        error: (e) => {
          this.carregandoLista = false;
          this.toast.error(e?.message || 'Falha ao carregar lançamentos.');
        },
      });
  }

  selecionarFiltroRapido(tipo: FiltroRapido): void {
    this.filtroRapido = tipo;
    const hoje = new Date();
    const fmt = (d: Date) => d.toISOString().slice(0, 10);

    if (tipo === 'HOJE') {
      this.dataInicio = fmt(hoje);
      this.dataFim = fmt(hoje);
    } else if (tipo === '7DIAS') {
      const d7 = new Date();
      d7.setDate(d7.getDate() - 6);
      this.dataInicio = fmt(d7);
      this.dataFim = fmt(hoje);
    } else if (tipo === 'MES') {
      const d1 = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
      this.dataInicio = fmt(d1);
      this.dataFim = fmt(hoje);
    } else {
      this.dataInicio = '';
      this.dataFim = '';
    }
    this.listar();
  }

  onChangeData(): void {
    this.filtroRapido = 'TODOS';
    this.listar();
  }
  onFiltroTipoChange(): void { this.listar(); }
  onFiltroOrigemChange(): void { this.listar(); }
  onBuscarChange(): void { this.aplicarFiltroLocal(); }

  aplicarFiltroLocal(): void {
    const term = this.normalizar(this.busca);
    const numTerm = this.onlyDigits(this.busca);
    let lista = [...this.base];

    if (term) {
      lista = lista.filter((l) => {
        const desc = this.normalizar(l.descricao || '');
        const autor = this.normalizar(l.criadoPorNome || '');
        const valor = Number(l.valor || 0);
        const valorFmt = valor.toLocaleString('pt-BR', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        });

        const hitDesc = desc.includes(term) || autor.includes(term);
        const hitValorText =
          this.normalizar(String(l.valor ?? '')).includes(term) ||
          this.normalizar(valorFmt).includes(term);
        const hitValorDigits = numTerm
          ? this.onlyDigits(valorFmt).includes(numTerm)
          : false;

        return hitDesc || hitValorText || hitValorDigits;
      });
    }

    this.view = lista;
    this.totalAtual = this.somar(lista);
    this.page = 1;
  }

  private somar(arr: Lancamento[]): number {
    return (arr || []).reduce((acc, l) => {
      const valor = Number(l.valor || 0);
      return l.tipo === 'ENTRADA' ? acc + valor : acc - valor;
    }, 0);
  }

  private normalizar(v: string): string {
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

  get lancamentosPaginados(): Lancamento[] {
    const start = (this.page - 1) * this.pageSize;
    return this.view.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.view.length / this.pageSize));
  }

  prevPage(): void {
    if (this.page > 1) this.page--;
  }

  nextPage(): void {
    if (this.page < this.totalPages) this.page++;
  }

  abrirNovo(): void {
    if (!this.podeCriar()) {
      this.toast.warning('Seu perfil não tem permissão para criar lançamentos.');
      return;
    }
    this.form = {
      id: undefined,
      tipo: 'SAIDA',
      origem: 'MANUAL',
      descricao: '',
      valorStr: '',
      formaPagamento: '',
    };
    this.valorFormatado = '';
    this.editSnapshot = null;
    this.selected = null;
    this.ultimoCriado = null;
    this.modal = 'novo';
    this.modalScroll.lock();
  }

  editar(item: Lancamento): void {
    if (!this.podeEditar()) {
      this.toast.warning('Somente ADMIN ou DEV podem editar.');
      return;
    }
    if (!item || item.cancelado) {
      this.toast.warning('Não é possível editar lançamento cancelado.');
      return;
    }

    this.editSnapshot = { ...item };
    this.form = {
      id: item.id,
      tipo: item.tipo,
      origem: item.origem,
      descricao: item.descricao ?? '',
      valorStr: String(item.valor ?? ''),
      formaPagamento: item.formaPagamento ?? '',
    };
    const num = Number(this.form.valorStr || 0);
    this.valorFormatado = isNaN(num)
      ? ''
      : num.toLocaleString('pt-BR', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        });
    this.selected = null;
    this.ultimoCriado = null;
    this.modal = 'editar';
    this.modalScroll.lock();
  }

  detalhes(item: Lancamento): void {
    if (!this.podeVerDetalhes()) return;
    this.selected = item;
    this.modal = 'detalhes';
    this.modalScroll.lock();
  }

  abrirModalCancelar(item: Lancamento): void {
    if (!this.podeCancelar()) {
      this.toast.warning('Somente ADMIN ou DEV podem cancelar.');
      return;
    }
    if (!item || item.cancelado) {
      this.toast.warning('Lançamento já está cancelado.');
      return;
    }
    this.selected = item;
    this.deleting = false;
    this.modal = 'cancelar';
    this.modalScroll.lock();
  }

  fecharModal(): void {
    this.modal = null;
    this.selected = null;
    this.editSnapshot = null;
    this.valorFormatado = '';
    this.deleting = false;
    this.estaCarregandoAcao = false;
    this.modalScroll.unlock();
  }

  onValorInput(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const raw = input.value.replace(/\D/g, '');
    if (!raw) {
      this.form.valorStr = '';
      this.valorFormatado = '';
      return;
    }
    const num = Number(raw) / 100;
    this.form.valorStr = String(num);
    this.valorFormatado = num.toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  permitirSomenteNumeros(ev: KeyboardEvent): void {
    const allow = ['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab', 'Enter'];
    if (allow.includes(ev.key)) return;
    if (ev.ctrlKey || ev.metaKey) return;
    if (!/[0-9]/.test(ev.key)) ev.preventDefault();
  }

  onValorPaste(ev: ClipboardEvent): void {
    ev.preventDefault();
    const text = ev.clipboardData?.getData('text') || '';
    const digits = text.replace(/\D/g, '');
    if (!digits) {
      this.form.valorStr = '';
      this.valorFormatado = '';
      return;
    }
    const num = Number(digits) / 100;
    this.form.valorStr = String(num);
    this.valorFormatado = num.toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  valorValido(): boolean {
    const n = Number(this.form.valorStr);
    return !isNaN(n) && n > 0;
  }

  salvar(): void {
    if (!this.form.descricao.trim()) {
      this.toast.warning('Informe a descrição.');
      return;
    }
    if (!this.valorValido()) {
      this.toast.warning('Valor deve ser maior que zero.');
      return;
    }

    this.estaCarregandoAcao = true;

    if (this.modal === 'novo') {
      const payload: CriarLancamentoPayload = {
        tipo: this.form.tipo,
        origem: this.form.origem || 'MANUAL',
        descricao: this.form.descricao.trim(),
        valor: Number(this.form.valorStr),
        formaPagamento: this.form.formaPagamento || undefined,
      };
      this.financeiro.criar(payload).subscribe({
        next: (novo) => {
          this.estaCarregandoAcao = false;
          this.toast.success('Lançamento registrado com sucesso!');
          this.ultimoCriado = novo;
          this.modal = 'sucesso';
          this.listar();
        },
        error: (e) => {
          this.estaCarregandoAcao = false;
          this.toast.error(e?.message || 'Falha ao criar lançamento.');
        },
      });
      return;
    }

    if (this.modal === 'editar' && this.form.id != null) {
      const payload: AtualizarLancamentoPayload = {
        descricao: this.form.descricao.trim(),
        valor: Number(this.form.valorStr),
        formaPagamento: this.form.formaPagamento || undefined,
      };

      if (this.editSnapshot) {
        const originalDesc = (this.editSnapshot.descricao || '').toString().trim();
        const originalVal = Number(this.editSnapshot.valor || 0);
        const originalForma = (this.editSnapshot.formaPagamento || '') as string;

        const novoDesc = payload.descricao || '';
        const novoVal = Number(payload.valor || 0);
        const novoForma = (payload.formaPagamento || '') as string;

        if (originalDesc === novoDesc && originalVal === novoVal && originalForma === novoForma) {
          this.toast.info('Nenhuma alteração detectada.');
          this.estaCarregandoAcao = false;
          return;
        }
      }

      this.financeiro.atualizar(this.form.id, payload).subscribe({
        next: () => {
          this.estaCarregandoAcao = false;
          this.toast.success('Lançamento atualizado com sucesso!');
          this.fecharModal();
          this.listar();
        },
        error: (e) => {
          this.estaCarregandoAcao = false;
          this.toast.error(e?.message || 'Falha ao atualizar lançamento.');
        },
      });
    }
  }

  confirmarCancelamento(): void {
    if (!this.selected) return;
    if (!this.podeCancelar()) {
      this.toast.warning('Somente ADMIN ou DEV podem cancelar.');
      return;
    }

    this.estaCarregandoAcao = true;
    this.deleting = true;

    const motivo = 'Cancelado manualmente pelo usuário.';
    this.financeiro.cancelar(this.selected.id, motivo).subscribe({
      next: () => {
        this.estaCarregandoAcao = false;
        this.toast.success('Lançamento cancelado.');
        this.fecharModal();
        this.listar();
      },
      error: (e) => {
        this.estaCarregandoAcao = false;
        this.deleting = false;
        this.toast.error(e?.message || 'Falha ao cancelar lançamento.');
      },
    });
  }

  isEntrada(item: Lancamento | null): boolean {
    return item?.tipo === 'ENTRADA';
  }

  tipoBadgeClass(t: TipoLancamento): string {
    return t === 'ENTRADA'
      ? 'bg-green-100 text-green-800 border-green-200'
      : 'bg-red-100 text-red-800 border-red-200';
  }

  tipoValorClass(l: Lancamento | null): string {
    if (l?.cancelado) return 'text-[var(--text-dim)] line-through';
    return this.isEntrada(l) ? 'text-green-500' : 'text-red-500';
  }

  moeda(v: number): string {
    return Number(v || 0).toLocaleString('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  hojeISO(): string {
    return new Date().toISOString().slice(0, 10);
  }

  truncate(v: string | undefined | null, len = 56): string {
    const s = (v || '').toString();
    return s.length <= len ? s : s.slice(0, len) + '...';
  }

  formatarAutor(nome?: string | null, id?: number | null): string {
    const n = (nome || '').trim();
    if (!n && !id) return '-';
    if (/\|\s*\d+\s*$/.test(n)) return n;
    if (id != null) return `${n}${n ? ' ' : ''}| ${id}`;
    return n || '-';
  }

  autorDa(s: Lancamento | null): string {
    if (!s) return '-';
    return this.formatarAutor(s.criadoPorNome, (s.criadoPorId as any) ?? null);
  }

  codigoDe(l: Lancamento): string {
    const d = l.data ? new Date(l.data) : new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const seq = String((l.id ?? 0) % 1000).padStart(3, '0');
    return `${y}${m}${seq}`;
  }

  private async loadJsPDF(): Promise<any> {
    try {
      const mod: any = await import('jspdf');
      return mod.jsPDF || (mod.default && mod.default.jsPDF);
    } catch {
      return await new Promise((resolve, reject) => {
        const id = 'jspdf-cdn';
        if (document.getElementById(id)) {
          resolve((window as any).jspdf?.jsPDF);
          return;
        }
        const s = document.createElement('script');
        s.id = id;
        s.src =
          'https://cdn.jsdelivr.net/npm/jspdf@2.5.1/dist/jspdf.umd.min.js';
        s.async = true;
        s.onload = () => resolve((window as any).jspdf?.jsPDF);
        s.onerror = reject;
        document.head.appendChild(s);
      });
    }
  }

  private async loadHtml2Canvas(): Promise<any> {
    if ((window as any).html2canvas) return (window as any).html2canvas;
    return await new Promise((resolve, reject) => {
      const id = 'html2canvas-cdn';
      if (document.getElementById(id)) {
        resolve((window as any).html2canvas);
        return;
      }
      const s = document.createElement('script');
      s.id = id;
      s.src =
        'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js';
      s.async = true;
      s.onload = () => resolve((window as any).html2canvas);
      s.onerror = reject;
      document.head.appendChild(s);
    });
  }

  gerarComprovantePdf(item: Lancamento | null): void {
    if (!item) return;
    this.downloadingComprovante = true;
    this.gerarPdfMantendoLayout(item)
      .catch((e) => {
        console.error(e);
        this.toast.error('Não foi possível gerar o PDF.');
      })
      .finally(() => (this.downloadingComprovante = false));
  }

  private async gerarPdfMantendoLayout(l: Lancamento): Promise<void> {
    const jsPDF = await this.loadJsPDF();
    const html2canvas = await this.loadHtml2Canvas();
    if (!jsPDF || !html2canvas) {
      throw new Error('Bibliotecas de PDF/canvas indisponíveis.');
    }

    const html = this.renderComprovanteHtml(l);

    const host = document.createElement('div');
    host.style.position = 'fixed';
    host.style.left = '-20000px';
    host.style.top = '0';
    host.style.width = '420px';
    host.style.zIndex = '-1';
    host.innerHTML =
      `<div id="comprovante-wrapper" style="padding:18px;background:#f3f4f6;">${html}</div>`;
    document.body.appendChild(host);

    const imgs = Array.from(host.querySelectorAll('img'));
    await Promise.all(
      imgs.map((img) => {
        if (img.complete) return Promise.resolve(true);
        return new Promise((res) => {
          img.onload = () => res(true);
          img.onerror = () => res(true);
        });
      })
    );

    const el = host.querySelector('#comprovante-wrapper') as HTMLElement;
    const canvas = await html2canvas(el, {
      scale: 2,
      useCORS: true,
      backgroundColor: null,
    });

    const imgData = canvas.toDataURL('image/png');
    const doc = new jsPDF({ unit: 'pt', format: 'a4' });
    const pageW = 595;
    const pageH = 842;
    const margin = 36;
    const maxW = pageW - margin * 2;
    const ratio = canvas.width / canvas.height;
    const imgW = Math.min(maxW, canvas.width);
    const imgH = imgW / ratio;

    const x = (pageW - imgW) / 2;
    let y = margin;
    if (imgH > pageH - margin * 2) {
      const scale = (pageH - margin * 2) / imgH;
      doc.addImage(
        imgData,
        'PNG',
        x,
        y,
        imgW * scale,
        imgH * scale,
        undefined,
        'FAST'
      );
    } else {
      doc.addImage(imgData, 'PNG', x, y, imgW, imgH, undefined, 'FAST');
    }

    const nomeArquivo = `comprovante-${l.tipo.toLowerCase()}-${this.codigoDe(
      l
    )}.pdf`;
    doc.save(nomeArquivo);

    document.body.removeChild(host);
  }

  private renderComprovanteHtml(l: Lancamento): string {
    return l.tipo === 'SAIDA'
      ? this.renderComprovanteSaidaHtml(l)
      : this.renderComprovanteEntradaHtml(l);
  }

  private renderComprovanteSaidaHtml(saida: Lancamento): string {
    const numeroSaida = this.codigoDe(saida);
    const responsavel = this.autorDa(saida);
    const emitidoEm = new Date();

    const blocoEdicao =
      (saida as any).qtdEdicoes > 0
        ? `<div style="font-size:12px;margin-top:6px;color:#f97316;background:#fff7ed;border:1px solid #fdba74;border-radius:6px;padding:5px 8px;">
          Editado (${(saida as any).qtdEdicoes}x) em ${
            (saida as any).ultimaEdicaoEm
              ? this.formatarDataHora((saida as any).ultimaEdicaoEm)
              : '-'
          }<br/>
          por ${this.formatarAutor(
            (saida as any).ultimaEdicaoPorNome,
            (saida as any).ultimaEdicaoPorId
          )}
        </div>`
        : '';

    return `
    <div style="position:relative;font-family:'system-ui',-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;width:380px;max-width:100%;margin:0 auto;padding:22px 20px 20px;background:#fff;color:#1f2937;overflow:hidden;border:1px solid #e5e7eb;border-radius:14px;">
      <div style="text-align:center;margin-bottom:16px;">
        <img src="assets/logo.png" alt="Logo" style="height:90px;display:block;margin:0 auto 6px auto;" />
        <div style="font-size:11px;color:#9ca3af;">CNPJ: 12.886.443/0001-00</div>
      </div>

      <div style="height:1px;background:#e5e7eb;margin:14px 0;"></div>

      <div style="font-size:18px;color:#233642;text-align:center;font-weight:700;margin-bottom:16px;letter-spacing:0.4px;">
        COMPROVANTE DE SAÍDA
      </div>

      <div style="font-size:13px;color:#6b7280;margin-bottom:8px;">
        Número da saída: <strong style="color:#111827;">${numeroSaida}</strong>
      </div>

      <div style="margin-bottom:14px;padding-bottom:10px;border-bottom:1px dashed #d1d5db;">
        <div style="font-size:14px;margin-bottom:6px;">
          <b style="color:#111827;">Descrição:</b>
          <span style="font-weight:600;color:#1f2937;">${saida.descricao ?? '-'}</span>
        </div>
        <div style="font-size:14px;margin-bottom:6px;">
          <b style="color:#111827;">Valor:</b>
          <span style="font-weight:700;color:#1f2937;">${this.moeda(saida.valor)}</span>
        </div>
        <div style="font-size:14px;margin-bottom:6px;">
          <b style="color:#111827;">Data:</b>
          <span style="font-weight:600;color:#1f2937;">${
            saida.data
              ? new Date(saida.data).toLocaleDateString('pt-BR')
              : new Date().toLocaleDateString('pt-BR')
          }</span>
        </div>
        <div style="font-size:13px;margin-top:6px;color:#4b5563;">
          Criada por: <b>${responsavel}</b>
        </div>
        ${blocoEdicao}
      </div>

      <div style="margin:20px 0 18px 0;text-align:center;">
        <div style="height:56px;display:flex;flex-direction:column;align-items:center;justify-content:flex-end;gap:6px;">
          <div style="font-family:'Brush Script MT','Segoe Script','Lucida Handwriting',cursive;font-size:30px;color:#111827;line-height:1;transform:skewX(-6deg);letter-spacing:0.5px;">
            ${responsavel.split('|')[0].trim()}
          </div>
          <div style="width:78%;height:1px;background:#d1d5db;margin-top:2px;"></div>
        </div>
        <div style="font-size:11px;color:#9ca3af;margin-top:6px;">Assinatura do responsável</div>
      </div>

      <div style="font-size:11px;text-align:center;color:#9ca3af;padding-top:10px;border-top:1px solid #e5e7eb;">
        Emitido em ${emitidoEm.toLocaleDateString('pt-BR')} às ${emitidoEm.toLocaleTimeString('pt-BR')}<br/>
        Saída Nº ${numeroSaida}
      </div>
    </div>`;
  }

  private renderComprovanteEntradaHtml(ent: Lancamento): string {
    const numero = this.codigoDe(ent);
    const responsavel = this.autorDa(ent);
    const emitidoEm = new Date();

    return `
    <div style="position:relative;font-family:'system-ui',-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;width:380px;max-width:100%;margin:0 auto;padding:22px 20px 20px;background:#fff;color:#1f2937;overflow:hidden;border:1px solid #e5e7eb;border-radius:14px;">
      <div style="text-align:center;margin-bottom:16px;">
        <img src="assets/logo.png" alt="Logo" style="height:90px;display:block;margin:0 auto 6px auto;" />
        <div style="font-size:11px;color:#9ca3af;">CNPJ: 12.886.443/0001-00</div>
      </div>

      <div style="height:1px;background:#e5e7eb;margin:14px 0;"></div>

      <div style="font-size:18px;color:#233642;text-align:center;font-weight:700;margin-bottom:16px;letter-spacing:0.4px;">
        COMPROVANTE DE ENTRADA
      </div>

      <div style="font-size:13px;color:#6b7280;margin-bottom:8px;">
        Número: <strong style="color:#111827;">${numero}</strong>
      </div>

      <div style="margin-bottom:14px;padding-bottom:10px;border-bottom:1px dashed #d1d5db;">
        <div style="font-size:14px;margin-bottom:6px;">
          <b style="color:#111827;">Descrição:</b>
          <span style="font-weight:600;color:#1f2937;">${ent.descricao ?? '-'}</span>
        </div>
        <div style="font-size:14px;margin-bottom:6px;">
          <b style="color:#111827;">Valor:</b>
          <span style="font-weight:700;color:#1f2937;">${this.moeda(ent.valor)}</span>
        </div>
        <div style="font-size:14px;margin-bottom:6px;">
          <b style="color:#111827;">Data:</b>
          <span style="font-weight:600;color:#1f2937;">${
            ent.data
              ? new Date(ent.data).toLocaleDateString('pt-BR')
              : new Date().toLocaleDateString('pt-BR')
          }</span>
        </div>
        <div style="font-size:13px;margin-top:6px;color:#4b5563;">
          Criada por: <b>${responsavel}</b>
        </div>
      </div>

      <div style="font-size:11px;text-align:center;color:#9ca3af;padding-top:10px;border-top:1px solid #e5e7eb;">
        Emitido em ${emitidoEm.toLocaleDateString('pt-BR')} às ${emitidoEm.toLocaleTimeString('pt-BR')}<br/>
        Entrada Nº ${numero}
      </div>
    </div>`;
  }

  private formatarDataHora(dt?: string | null): string {
    if (!dt) return '-';
    const d = new Date(dt);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(
      d.getHours()
    )}:${pad(d.getMinutes())}`;
  }
}
