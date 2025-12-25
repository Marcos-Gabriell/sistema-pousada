import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import {
  UsersService,
  Usuario,
  PaginacaoUsuarios,
  CriarUsuarioPayload,
  AtualizarUsuarioPayload,
  UsuarioCriadoResponse,
  ResetSenhaResponse,
  AlterarStatusResponse,
  AvatarView
} from './users.service';
import { AuthService, Role } from '../../services/login/auth.service';
import { Router } from '@angular/router';
import { ToastService } from '../../toast/toast.service';
import { map, finalize } from 'rxjs/operators';
import { ModalScrollService } from '../../services/modal-scroll.service';

type UsuarioView = Usuario & {
  codigo?: string | null;
  diasParaExclusao?: number | null;

  criadoEm?: string | Date | null;
  criadoPorId?: number | null;
  criadoPorNome?: string | null;

  // ✅ somente online/offline
  online?: boolean | null;
};

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.css']
})
export class UsersComponent implements OnInit {
  usuarios: UsuarioView[] = [];
  pagina = 1;
  tamanho = 5;
  totalPaginas = 1;
  totalElementos = 0;
  carregandoLista = false;

  estaProcessandoAcao = false;
  usuarioEmProcessamentoId: number | null = null;

  filtroBusca = '';
  filtroStatus = '';
  filtroPerfil = '';

  modalAberto = false;
  editando = false;
  usuarioEditando: UsuarioView | null = null;

  confirmarExcluirAberto = false;
  usuarioParaExcluir: UsuarioView | null = null;

  detalhesAberto = false;
  usuarioDetalhes: UsuarioView | null = null;

  detalhesAvatar: AvatarView | null = null;
  detalhesAvatarDataUrl: string | null = null;

  confirmarResetAberto = false;
  usuarioParaReset: UsuarioView | null = null;

  confirmarStatusAberto = false;
  usuarioParaStatus: UsuarioView | null = null;
  proximoStatus: boolean | null = null;

  modalSenhaAberto = false;
  senhaGerada: string | null = null;
  contadorSegundos = 60;
  private timerRef: any = null;
  mostrarSenha = false;

  perfisDisponiveis: Role[] = ['GERENTE'];
  roleAtual: Role = 'GERENTE';

  isAdmin = false;
  isDev = false;
  isAdminOrDev = false;

  form: {
    nome: string;
    email: string;
    username?: string;
    numero?: string;
    role: Role;
    ativo: boolean;
  } = {
    nome: '',
    email: '',
    username: '',
    numero: '',
    role: 'GERENTE',
    ativo: true
  };

  contagemExibida = 0;
  contagemAtivos = 0;
  contagemInativos = 0;

  emailHintsOpen = false;
  emailActiveIndex = 0;
  emailCandidates: string[] = [];
  private readonly commonDomains = [
    'gmail.com', 'outlook.com', 'yahoo.com', 'aol.com',
    'hotmail.com', 'icloud.com', 'live.com', 'proton.me'
  ];

  constructor(
    private usersService: UsersService,
    private toast: ToastService,
    private auth: AuthService,
    private router: Router,
    private modalScroll: ModalScrollService
  ) {}

  ngOnInit(): void {
    const roles = (this.auth.getRoles?.() ?? []) as Role[];
    this.isAdmin = roles.includes('ADMIN');
    this.isDev   = roles.includes('DEV');
    const isGerente = roles.includes('GERENTE');
    this.isAdminOrDev = this.isAdmin || this.isDev;

    this.roleAtual = this.isAdmin ? 'ADMIN' : (this.isDev ? 'DEV' : (isGerente ? 'GERENTE' : 'GERENTE'));

    const allowedToView = this.isAdmin || this.isDev || isGerente;
    if (!allowedToView) {
      this.toast.warning('Acesso restrito.');
      this.router.navigate(['/dashboard']);
      return;
    }

    this.perfisDisponiveis = this.isDev ? (['ADMIN', 'GERENTE'] as Role[]) : (['GERENTE'] as Role[]);
    this.buscar();
  }

  onEmailInput(ev: Event) {
    const value = (ev.target as HTMLInputElement).value || '';
    this.form.email = value;

    const atIndex = value.indexOf('@');
    if (atIndex < 0) { this.closeEmailHints(); return; }

    const local = value.slice(0, atIndex).trim();
    const after = value.slice(atIndex + 1).toLowerCase();

    if (!local) { this.closeEmailHints(); return; }

    const list = !after
      ? this.commonDomains
      : this.commonDomains.filter(d => d.startsWith(after));

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

  onEmailBlur() { setTimeout(() => this.closeEmailHints(), 100); }

  applyEmailDomain(domain: string) {
    const value = (this.form.email || '').toString();
    const atIndex = value.indexOf('@');
    if (atIndex < 0) return;

    const local = value.slice(0, atIndex).trim();
    this.form.email = `${local}@${domain}`;
    this.closeEmailHints();
  }

  private closeEmailHints() {
    this.emailHintsOpen = false;
    this.emailCandidates = [];
    this.emailActiveIndex = 0;
  }

  somenteDigitos(v: string | null | undefined): string {
    return (v || '').replace(/\D/g, '');
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

  telefoneOpcionalValido(v: string | null | undefined): boolean {
    const d = this.somenteDigitos(v || '');
    return d.length === 0 || d.length === 11;
  }

  private numeroParaBackend(v: string | null | undefined): string | undefined {
    const d = this.somenteDigitos(v || '');
    return d ? d : undefined;
  }

  isEmailValido(e?: string): boolean {
    if (!e) return false;
    return /.+@.+\..+/.test(e.trim());
  }

  bloquearNaoDigitos(ev: KeyboardEvent) {
    const k = ev.key;
    if (!/[0-9]/.test(k) && !['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab'].includes(k)) {
      ev.preventDefault();
    }
  }

  onNumeroPaste(ev: ClipboardEvent) {
    ev.preventDefault();
    const text = ev.clipboardData?.getData('text') || '';
    const digs = this.somenteDigitos(text).slice(0, 11);
    (ev.target as HTMLInputElement).value = this.formatarTelefonePadrao(digs);
    this.form.numero = (ev.target as HTMLInputElement).value;
  }

  onNumeroInput(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const digs = this.somenteDigitos(input.value).slice(0, 11);
    input.value = this.formatarTelefonePadrao(digs);
    this.form.numero = input.value;
  }

  private mostrarErroApi(e: any, fallback: string) {
    const raw = e?.error ?? e;
    const pick = (o: any) => o?.mensagem || o?.message || o?.error || o?.statusText || '';
    let texto = '';
    if (typeof raw === 'string') {
      try { texto = pick(JSON.parse(raw)) || raw; }
      catch { texto = raw; }
    } else {
      texto = pick(raw);
    }
    texto = String(texto || '').trim();

    if (/E-mail já cadastrado/i.test(texto)) { this.toast.error('E-mail já cadastrado!'); return; }
    if (/Username já cadastrado/i.test(texto)) { this.toast.error('Usuário já cadastrado!'); return; }
    if (/Número já cadastrado|Telefone já cadastrado/i.test(texto)) { this.toast.error('Telefone já cadastrado!'); return; }
    if (/Não autenticado/i.test(texto)) { this.toast.error('Sessão inválida. Faça login novamente.'); return; }
    if (/Usuário não encontrado/i.test(texto)) { this.toast.error('Usuário não encontrado.'); return; }
    this.toast.error(texto || fallback);
  }

  buscar() {
    this.carregandoLista = true;

    this.usersService
      .listar(this.pagina, this.tamanho, this.filtroBusca, this.filtroStatus, this.filtroPerfil)
      .pipe(
        map((p: PaginacaoUsuarios) => {
          const roleOrder: Record<string, number> = { DEV: 1, ADMIN: 2, GERENTE: 3, FUNCIONARIO: 4 };

          const usuarios = (p?.conteudo ?? []).map((u) => {
            const view: UsuarioView = {
              ...u,
              diasParaExclusao:
                (u as any).diasParaExclusao ??
                (u as any).diasRestantesExclusao ??
                (u as any).diasRestantesExclusao ??
                null,
              criadoPorId:   (u as any).criadoPorId   ?? (u as any).criadoPor?.id   ?? null,
              criadoPorNome: (u as any).criadoPorNome ?? (u as any).criadoPor?.nome ?? null,
              online: (u as any).online ?? null
            };
            return view;
          }) as UsuarioView[];

          usuarios.sort((a, b) => {
            const aOrder = roleOrder[String(a.role || '').toUpperCase()] ?? 99;
            const bOrder = roleOrder[String(b.role || '').toUpperCase()] ?? 99;
            if (aOrder !== bOrder) return aOrder - bOrder;
            return String(a.nome || '').localeCompare(String(b.nome || ''), 'pt-BR', { sensitivity: 'base' });
          });

          return {
            usuarios,
            pagina: p?.pagina ?? 1,
            tamanho: p?.tamanho ?? this.tamanho,
            totalPaginas: p?.totalPaginas ?? 1,
            totalElementos: p?.totalElementos ?? usuarios.length,
          };
        }),
        finalize(() => (this.carregandoLista = false))
      )
      .subscribe({
        next: (r) => {
          this.usuarios = r.usuarios;
          this.pagina = r.pagina;
          this.tamanho = r.tamanho;
          this.totalPaginas = r.totalPaginas;
          this.totalElementos = r.totalElementos;

          this.contagemExibida = this.usuarios.length;
          this.contagemAtivos = this.usuarios.filter((u) => !!u.ativo).length;
          this.contagemInativos = this.usuarios.filter((u) => !u.ativo).length;
        },
        error: (e) => {
          this.mostrarErroApi(e, 'Falha ao carregar usuários.');
          this.contagemExibida = 0;
          this.contagemAtivos = 0;
          this.contagemInativos = 0;
        },
      });
  }

  limparFiltros() {
    this.filtroBusca = '';
    this.filtroStatus = '';
    this.filtroPerfil = '';
    this.pagina = 1;
    this.buscar();
  }

  irPara(p: number) {
    if (p < 1 || p > this.totalPaginas) return;
    this.pagina = p;
    this.buscar();
  }

  podeCriar(): boolean { return this.isAdminOrDev; }

  private isAdminUser(u: UsuarioView): boolean {
    return String(u.role || '').toUpperCase() === 'ADMIN';
  }
  private isDevUser(u: UsuarioView): boolean {
    return String(u.role || '').toUpperCase() === 'DEV';
  }

  podeEditar(u: UsuarioView): boolean {
    if (!this.isAdminOrDev) return false;
    if (this.isDev) return !this.isDevUser(u);
    return !this.isAdminUser(u) && !this.isDevUser(u);
  }

  podeResetar(u: UsuarioView): boolean { return this.podeEditar(u); }
  podeExcluir(u: UsuarioView): boolean { return this.podeEditar(u); }
  podeAlternarStatus(u: UsuarioView): boolean { return this.podeEditar(u); }

  abrirDetalhes(u: UsuarioView) {
    this.usuarioDetalhes = u;
    this.detalhesAberto = true;
    this.modalScroll.lock();

    this.detalhesAvatar = null;
    this.detalhesAvatarDataUrl = null;

    if (!u?.id) return;
    this.usersService.getAvatar(u.id).subscribe({
      next: (v: AvatarView) => {
        this.detalhesAvatar = v || null;
        this.detalhesAvatarDataUrl = v?.dataUrl ?? null;
      },
      error: () => {
        this.detalhesAvatar = null;
        this.detalhesAvatarDataUrl = null;
      }
    });
  }

  fecharDetalhes() {
    this.usuarioDetalhes = null;
    this.detalhesAberto = false;
    this.modalScroll.unlock();
    this.detalhesAvatar = null;
    this.detalhesAvatarDataUrl = null;
  }

  getAvatarSafe(u?: UsuarioView | null): string | null {
    if (!u || !this.usuarioDetalhes) return null;
    if (u.id !== this.usuarioDetalhes.id) return null;
    return this.detalhesAvatarDataUrl || null;
  }

  getAvatarBg(user?: UsuarioView | null): string {
    const n = (user?.nome || '').trim();
    if (!n) return '#94a3b8';
    let h = 0;
    for (let i = 0; i < n.length; i++) h = (h * 31 + n.charCodeAt(i)) >>> 0;
    const hue = h % 360;
    const c1 = `hsl(${hue} 65% 55%)`;
    const c2 = `hsl(${(hue + 30) % 360} 60% 45%)`;
    return `linear-gradient(135deg, ${c1}, ${c2})`;
  }

  getInitials(nome?: string | null): string {
    if (!nome) return '';
    const parts = nome.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  abrirNovo() {
    if (!this.podeCriar()) {
      this.toast.warning('Apenas ADMIN/DEV pode criar usuários.');
      return;
    }
    this.editando = false;
    this.usuarioEditando = null;
    this.form = {
      nome: '',
      email: '',
      username: '',
      numero: '',
      role: this.perfisDisponiveis[0] || 'GERENTE',
      ativo: true
    };
    this.estaProcessandoAcao = false;
    this.usuarioEmProcessamentoId = null;
    this.modalAberto = true;
    this.modalScroll.lock();
  }

  abrirEdicao(u: UsuarioView) {
    if (!this.podeEditar(u)) {
      this.toast.warning('Ações indisponíveis para seu perfil.');
      return;
    }
    this.detalhesAberto = false;
    this.usuarioDetalhes = null;

    this.editando = true;
    this.usuarioEditando = u;

    this.form = {
      nome: u.nome,
      email: u.email || '',
      username: u.username || '',
      numero: u.numero ? this.formatarTelefonePadrao(this.somenteDigitos(u.numero)) : '',
      role: (this.isAdminOrDev ? (u.role as Role) : 'GERENTE'),
      ativo: u.ativo
    };

    this.estaProcessandoAcao = false;
    this.usuarioEmProcessamentoId = null;
    this.modalAberto = true;
    this.modalScroll.lock();
  }

  fecharModal() {
    this.modalAberto = false;
    this.modalScroll.unlock();
    this.estaProcessandoAcao = false;
    this.usuarioEmProcessamentoId = null;
  }

  private houveAlteracao(u: UsuarioView | null, f: any): boolean {
    if (!u) return true;
    const numOrig = this.somenteDigitos(u.numero || '');
    const numForm = this.somenteDigitos(f.numero || '');
    return (
      (u.nome || '') !== (f.nome || '') ||
      (u.email || '') !== (f.email || '') ||
      (u.username || '') !== (f.username || '') ||
      numOrig !== numForm ||
      (u.role || '') !== (f.role || '') ||
      !!u.ativo !== !!f.ativo
    );
  }

  validarForm(): boolean {
    const nomeOk = (this.form.nome || '').trim().length >= 3;
    const emailOk = this.isEmailValido(this.form.email);
    const userOk  = !this.form.username || (this.form.username || '').trim().length >= 3;
    const roleOk  = this.perfisDisponiveis.includes(this.form.role);
    const foneOk  = this.telefoneOpcionalValido(this.form.numero);
    return nomeOk && emailOk && userOk && roleOk && foneOk;
  }

  private sucessoPosCriacao(senha?: string | null) {
    this.toast.success('Usuário criado com sucesso!');
    this.modalAberto = false;
    this.modalScroll.unlock();
    this.estaProcessandoAcao = false;
    this.usuarioEmProcessamentoId = null;
    if (senha) this.abrirModalSenha(senha);
    this.pagina = 1;
    this.buscar();
  }

  salvar(f: NgForm) {
    if (!f.valid || !this.validarForm()) {
      this.toast.warning('Preencha os campos obrigatórios corretamente.');
      return;
    }

    if (this.editando && this.usuarioEditando) {
      if (!this.houveAlteracao(this.usuarioEditando, this.form)) {
        this.toast.info('Nenhuma alteração detectada.');
        return;
      }

      const numDigits = this.somenteDigitos(this.form.numero || '');
      const payload: AtualizarUsuarioPayload = {
        nome: this.form.nome?.trim(),
        email: (this.form.email || '').trim(),
        username: (this.form.username ?? '').trim(),
        numero: numDigits === '' ? '' : numDigits,
        role: this.form.role,
        ativo: this.form.ativo
      };

      this.estaProcessandoAcao = true;
      this.usuarioEmProcessamentoId = this.usuarioEditando.id;

      this.usersService.atualizar(this.usuarioEditando.id, payload).subscribe({
        next: () => {
          this.toast.success('Usuário atualizado com sucesso!');
          this.modalAberto = false;
          this.modalScroll.unlock();
          this.estaProcessandoAcao = false;
          this.usuarioEmProcessamentoId = null;
          this.buscar();
        },
        error: (e) => {
          this.estaProcessandoAcao = false;
          this.usuarioEmProcessamentoId = null;
          this.mostrarErroApi(e, 'Falha ao atualizar usuário.');
        }
      });
      return;
    }

    if (!this.podeCriar()) {
      this.toast.warning('Apenas ADMIN/DEV pode criar.');
      return;
    }

    const payload: CriarUsuarioPayload = {
      nome: (this.form.nome || '').trim(),
      username: (this.form.username ?? '').trim() || undefined,
      role: this.form.role,
      ativo: this.form.ativo,
      email: (this.form.email || '').trim(),
      numero: this.numeroParaBackend(this.form.numero) ?? null
    };

    this.estaProcessandoAcao = true;
    this.usuarioEmProcessamentoId = null;

    this.usersService.criar(payload).subscribe({
      next: (res: UsuarioCriadoResponse) => {
        const senha = res?.senhaTemporaria || null;
        this.sucessoPosCriacao(senha);
      },
      error: (e) => {
        this.estaProcessandoAcao = false;
        this.usuarioEmProcessamentoId = null;
        this.mostrarErroApi(e, 'Falha ao criar usuário.');
      }
    });
  }

  pedirConfirmacaoStatus(u: UsuarioView) {
    if (!this.podeAlternarStatus(u)) {
      this.toast.warning('Ações indisponíveis para seu perfil.');
      return;
    }
    this.usuarioParaStatus = u;
    this.proximoStatus = !u.ativo;
    this.confirmarStatusAberto = true;
    this.modalScroll.lock();
    this.estaProcessandoAcao = false;
    this.usuarioEmProcessamentoId = null;
  }

  cancelarStatus() {
    this.confirmarStatusAberto = false;
    this.modalScroll.unlock();
    this.usuarioParaStatus = null;
    this.proximoStatus = null;
    this.estaProcessandoAcao = false;
    this.usuarioEmProcessamentoId = null;
  }

  confirmarStatus() {
    if (!this.usuarioParaStatus || this.proximoStatus === null) return;

    const alvo = this.usuarioParaStatus;
    const desejado = this.proximoStatus;

    this.estaProcessandoAcao = true;
    this.usuarioEmProcessamentoId = alvo.id;

    this.usersService.alternarStatus(alvo.id, desejado).subscribe({
      next: (res: AlterarStatusResponse & { diasParaExclusao?: number | null }) => {
        const aplicado = typeof res?.ativo === 'boolean' ? res.ativo : desejado;

        const i = this.usuarios.findIndex(x => x.id === alvo.id);
        if (i >= 0) {
          this.usuarios[i] = {
            ...this.usuarios[i],
            ativo: aplicado,
            diasParaExclusao: (res as any)?.diasParaExclusao ?? this.usuarios[i].diasParaExclusao
          };
        }

        const nome = (alvo?.nome || 'Usuário').trim();
        const msg  = aplicado
          ? `O usuário ${nome} foi ativado com sucesso.`
          : `O usuário ${nome} foi desativado com sucesso.`;

        (aplicado ? this.toast.success(msg) : this.toast.warning(msg));
        this.cancelarStatus();
      },
      error: (e) => {
        this.mostrarErroApi(e, 'Falha ao alterar status.');
        this.cancelarStatus();
      }
    });
  }

  pedirConfirmacaoExcluir(u: UsuarioView) {
    if (!this.podeExcluir(u)) {
      this.toast.warning('Ações indisponíveis para seu perfil.');
      return;
    }
    this.usuarioParaExcluir = u;
    this.confirmarExcluirAberto = true;
    this.modalScroll.lock();
    this.estaProcessandoAcao = false;
    this.usuarioEmProcessamentoId = null;
  }

  cancelarExclusao() {
    this.confirmarExcluirAberto = false;
    this.modalScroll.unlock();
    this.usuarioParaExcluir = null;
    this.estaProcessandoAcao = false;
    this.usuarioEmProcessamentoId = null;
  }

  confirmarExclusao() {
    if (!this.usuarioParaExcluir) return;

    const alvo = this.usuarioParaExcluir;
    this.estaProcessandoAcao = true;
    this.usuarioEmProcessamentoId = alvo.id;

    this.usersService.excluir(alvo.id).subscribe({
      next: () => {
        this.toast.success('Usuário excluído com sucesso!');
        this.confirmarExcluirAberto = false;
        this.usuarioParaExcluir = null;
        this.estaProcessandoAcao = false;
        this.usuarioEmProcessamentoId = null;
        this.buscar();
      },
      error: (e) => {
        this.estaProcessandoAcao = false;
        this.usuarioEmProcessamentoId = null;
        this.mostrarErroApi(e, 'Falha ao excluir usuário.');
      }
    });
  }

  pedirConfirmacaoReset(u: UsuarioView) {
    if (!this.podeResetar(u)) {
      this.toast.warning('Ações indisponíveis para seu perfil.');
      return;
    }
    this.usuarioParaReset = u;
    this.confirmarResetAberto = true;
    this.modalScroll.lock();
    this.estaProcessandoAcao = false;
    this.usuarioEmProcessamentoId = null;
  }

  cancelarReset() {
    this.confirmarResetAberto = false;
    this.modalScroll.unlock();
    this.usuarioParaReset = null;
    this.estaProcessandoAcao = false;
    this.usuarioEmProcessamentoId = null;
  }

  confirmarReset() {
    if (!this.usuarioParaReset) return;

    const alvo = this.usuarioParaReset;
    this.estaProcessandoAcao = true;
    this.usuarioEmProcessamentoId = alvo.id;

    this.usersService.resetarSenha(alvo.id).subscribe({
      next: (res: ResetSenhaResponse) => {
        const nova = res?.senhaTemporaria || null;
        this.confirmarResetAberto = false;
        this.estaProcessandoAcao = false;
        this.usuarioEmProcessamentoId = null;
        this.toast.success(res?.mensagem || 'Senha redefinida com sucesso.');
        this.abrirModalSenha(nova);
      },
      error: (e) => {
        this.confirmarResetAberto = false;
        this.estaProcessandoAcao = false;
        this.usuarioEmProcessamentoId = null;
        this.mostrarErroApi(e, 'Falha ao redefinir senha.');
      }
    });
  }

  private iniciarContador() {
    this.pararContador();
    this.contadorSegundos = 60;
    this.timerRef = setInterval(() => {
      this.contadorSegundos--;
      if (this.contadorSegundos <= 0) this.fecharModalSenha();
    }, 1000);
  }

  private pararContador() {
    if (this.timerRef) {
      clearInterval(this.timerRef);
      this.timerRef = null;
    }
  }

  abrirModalSenha(senha: string | null) {
    this.senhaGerada = senha;
    this.mostrarSenha = false;
    this.modalSenhaAberto = true;
    this.modalScroll.lock();
    this.iniciarContador();
  }

  fecharModalSenha() {
    this.modalSenhaAberto = false;
    this.modalScroll.unlock();
    this.senhaGerada = null;
    this.pararContador();
  }

  toggleMostrarSenha() { this.mostrarSenha = !this.mostrarSenha; }

  async copiarSenha() {
    try {
      await navigator.clipboard.writeText(this.senhaGerada || '');
      this.toast.info('Senha copiada para a área de transferência.');
    } catch {
      this.toast.warning('Não foi possível copiar automaticamente.');
    }
  }

  formatCountdown(s: number): string {
    const m = Math.floor(s / 60);
    const r = s % 60;
    return `${String(m).padStart(2, '0')}:${String(r).padStart(2, '0')}`;
  }

  private getDiasParaExclusao(u?: UsuarioView | null): number | null {
    if (!u) return null;
    const d = (u as UsuarioView).diasParaExclusao;
    if (d === undefined || d === null) return null;
    const n = Number(d);
    return Number.isFinite(n) ? n : null;
  }

  public hasDias(u?: UsuarioView | null): boolean {
    return !!u && !u.ativo && this.getDiasParaExclusao(u) !== null;
  }

  public getDias(u?: UsuarioView | null): number | '' {
    const n = this.getDiasParaExclusao(u);
    return n === null ? '' : n;
  }
}
