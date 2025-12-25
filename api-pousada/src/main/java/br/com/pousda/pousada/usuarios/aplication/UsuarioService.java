package br.com.pousda.pousada.usuarios.aplication;

import br.com.pousda.pousada.notificacoes.application.facade.NotifierFacade;
import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.domain.UsuarioUsernameAudit;
import br.com.pousda.pousada.usuarios.domain.enums.AvatarMode;
import br.com.pousda.pousada.usuarios.dtos.*;
import br.com.pousda.pousada.usuarios.infra.UsuarioAcessoLogRepository;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import br.com.pousda.pousada.usuarios.infra.UsuarioUsernameAuditRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final String ROLE_DEV = "DEV";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_GERENTE = "GERENTE";

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int USERNAME_CHANGE_LIMIT = 2;
    private static final int USERNAME_CHANGE_WINDOW_DAYS = 30;

    // ✅ Janela para considerar online (2 min)
    private static final long ONLINE_WINDOW_SECONDS = 120;

    private final UsuarioRepository repo;
    private final BCryptPasswordEncoder encoder;
    private final NotifierFacade notifier;
    private final UsuarioUsernameAuditRepository usernameAuditRepo;
    private final UsuarioAcessoLogRepository acessoLogRepo;

    // =========================================================
    // LISTAR
    // =========================================================
    public Map<String, Object> listar(int pagina, int tamanho, String q, String status, String perfil) {
        if (pagina < 1) pagina = 1;
        if (tamanho < 1) tamanho = 10;

        List<Usuario> all = repo.findAll();

        if (!isBlank(q)) {
            String b = q.trim().toLowerCase(Locale.ROOT);
            all = all.stream().filter(u ->
                    (u.getNome() != null && u.getNome().toLowerCase().contains(b)) ||
                            (u.getEmail() != null && u.getEmail().toLowerCase().contains(b)) ||
                            (u.getUsername() != null && u.getUsername().toLowerCase().contains(b)) ||
                            (u.getNumero() != null && u.getNumero().toLowerCase().contains(b))
            ).collect(Collectors.toList());
        }

        if (!isBlank(status)) {
            boolean ativo = "ATIVOS".equalsIgnoreCase(status);
            all = all.stream().filter(u -> u.isAtivo() == ativo).collect(Collectors.toList());
        }

        if (!isBlank(perfil)) {
            String p = perfil.trim().toUpperCase(Locale.ROOT);
            all = all.stream().filter(u -> p.equals(u.getRole())).collect(Collectors.toList());
        }

        all.sort(Comparator.comparing(u -> Optional.ofNullable(u.getNome()).orElse("")));

        int total = all.size();
        int from = Math.min((pagina - 1) * tamanho, total);
        int to = Math.min(from + tamanho, total);
        List<Usuario> janela = (from < to) ? all.subList(from, to) : Collections.emptyList();

        List<UsuarioResponseDTO> conteudo = janela.stream().map(u -> {
            u.setDiasParaExclusao(diasParaExclusaoDo(u));
            // sem ator aqui; não expõe dados sensíveis de login (regra do toDTO)
            return toDTO(u, null);
        }).collect(Collectors.toList());

        int totalPaginas = (int) Math.ceil((double) total / (double) tamanho);

        Map<String, Object> body = new HashMap<>();
        body.put("conteudo", conteudo);
        body.put("pagina", pagina);
        body.put("tamanho", tamanho);
        body.put("totalPaginas", Math.max(totalPaginas, 1));
        body.put("totalElementos", total);
        return body;
    }

    public List<String> listarPerfis(Usuario ator) {
        if (isDev(ator)) return Arrays.asList(ROLE_ADMIN, ROLE_GERENTE);
        if (isAdmin(ator)) return Collections.singletonList(ROLE_GERENTE);
        return Collections.emptyList();
    }

    @Deprecated
    public List<String> listarPerfis() {
        return Arrays.asList(ROLE_ADMIN, ROLE_GERENTE);
    }

    // =========================================================
    // CRIAÇÃO
    // =========================================================
    @Transactional
    public Map<String, Object> criar(Usuario criador, CriarUsuarioDTO dto) {
        if (criador == null) throw new IllegalStateException("Não autenticado.");
        if (!isDev(criador) && !isAdmin(criador))
            throw new SecurityException("Somente DEV ou ADMIN podem criar usuários.");

        validarCriacao(dto);

        String user   = isBlank(dto.getUsername()) ? null : dto.getUsername().trim();
        String numero = isBlank(dto.getNumero())   ? null : dto.getNumero().trim();
        String email  = dto.getEmail().trim();
        String nome   = dto.getNome().trim();

        String reqRole = Optional.ofNullable(dto.getRole())
                .map(String::trim).map(String::toUpperCase).orElse(ROLE_GERENTE);

        String roleToApply;
        if (ROLE_ADMIN.equals(reqRole)) {
            if (!isDev(criador)) {
                throw new SecurityException("Apenas DEV pode criar usuário ADMIN.");
            }
            roleToApply = ROLE_ADMIN;
        } else {
            roleToApply = ROLE_GERENTE;
        }

        String senhaTemporaria = gerarSenhaTemporaria(10);

        // ===== código único: ANO + sequência global =====
        String codigoGerado = gerarCodigoAnoSequencialGlobal();

        Usuario u = new Usuario();
        u.setCodigo(codigoGerado);
        u.setNome(nome);
        u.setEmail(email);
        u.setNumero(numero);
        u.setUsername(user);
        u.setPassword(encoder.encode(senhaTemporaria));
        u.setRole(roleToApply);

        // 🔒 TEMA: sempre começa como "claro"
        u.setTema("claro");

        u.setAtivo(true);
        u.setBootstrapAdmin(false);
        u.setMustChangePassword(true);
        u.setPwdChangeReason("FIRST_LOGIN");
        u.setLastPasswordChangeAt(null);
        u.setInativadoEm(null);

        // login tracking inicial
        u.setUltimoLoginEm(null);
        u.setUltimoLoginIp(null);

        u.setCriadoPor(criador);
        u.setCriadoPorNome(Optional.ofNullable(criador.getNome()).orElse("Desconhecido"));

        try {
            repo.saveAndFlush(u);
        } catch (DataIntegrityViolationException ex) {
            log.warn("[USUARIO] Colisão de codigo={} detectada; regenerando...", codigoGerado);
            u.setCodigo(gerarCodigoAnoSequencialGlobal());
            repo.saveAndFlush(u);
        }

        try { notifier.usuarioCriado(criador, u); }
        catch (Exception e) {
            log.warn("Falha ao notificar 'usuarioCriado' para userId={}: {}", u.getId(), e.getMessage());
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mensagem", "Usuário cadastrado com sucesso! Senha temporária gerada.");
        resp.put("id", u.getId());
        resp.put("codigo", u.getCodigo());
        resp.put("nome", u.getNome());
        resp.put("username", u.getUsername());
        resp.put("email", u.getEmail());
        resp.put("numero", u.getNumero());
        resp.put("role", u.getRole());
        resp.put("ativo", u.isAtivo());
        resp.put("senhaTemporaria", senhaTemporaria);
        resp.put("criadoEm", u.getCriadoEm());
        resp.put("criadoPorId",   (u.getCriadoPor() != null) ? u.getCriadoPor().getId() : null);
        resp.put("criadoPorNome", u.getCriadoPorNome());
        return resp;
    }

    /** ANO + sequência global crescente (mín. 3 dígitos no sufixo). */
    private String gerarCodigoAnoSequencialGlobal() {
        int ano = LocalDate.now(SP).getYear();
        Long max = repo.maxCodigoSequencia(); // pode retornar null
        long nextSeq = (max == null ? 0L : max) + 1L;
        String sufixo = (nextSeq < 1000) ? String.format("%03d", nextSeq) : String.valueOf(nextSeq);
        return String.valueOf(ano) + sufixo;
    }

    // =========================================================
    // ATUALIZAÇÃO (com diff)
    // =========================================================
    @Transactional
    public UsuarioResponseDTO atualizar(Usuario ator, Long id, AtualizarUsuarioDTO dto) {
        if (ator == null) throw new IllegalStateException("Não autenticado.");

        Usuario alvo = repo.findById(id).orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));
        ensureCanManage(ator, alvo, "editar");

        if (isBootstrapAdmin(alvo)) throw new SecurityException("Dados do ADMIN principal são imutáveis.");

        Usuario before = snapshot(alvo);

        if (dto.getNome() != null) {
            String nome = dto.getNome().trim();
            if (isBlank(nome)) throw new IllegalArgumentException("Nome é obrigatório.");
            if (nome.length() > 15) throw new IllegalArgumentException("Nome pode ter no máximo 15 caracteres.");
            alvo.setNome(nome);
        }

        if (dto.getEmail() != null) {
            String email = dto.getEmail().trim();
            if (isBlank(email)) throw new IllegalArgumentException("E-mail é obrigatório.");
            if (repo.existsByEmailAndIdNot(email, id)) throw new IllegalArgumentException("E-mail já cadastrado!");
            alvo.setEmail(email);
        }

        if (dto.getNumero() != null) {
            String numero = dto.getNumero().trim();
            if (numero.isEmpty()) {
                alvo.setNumero(null);
            } else {
                if (repo.existsByNumeroAndIdNot(numero, id))
                    throw new IllegalArgumentException("Número já cadastrado!");
                alvo.setNumero(numero);
            }
        }

        if (dto.getUsername() != null) {
            String user = dto.getUsername().trim();
            if (user.isEmpty()) {
                alvo.setUsername(null);
            } else {
                if (repo.existsByUsernameAndIdNot(user, id))
                    throw new IllegalArgumentException("Username já cadastrado!");

                String oldUsername = alvo.getUsername();
                if (!user.equals(oldUsername)) {
                    validarTrocaUsername(alvo);
                    alvo.setUsername(user);
                    registrarTrocaUsername(alvo, oldUsername, user);
                }
            }
        }

        if (dto.getRole() != null) {
            String novo = roleAlvo(dto.getRole());
            if (!canChangeRole(ator, alvo, novo))
                throw new SecurityException("Você não tem permissão para alterar o perfil deste usuário.");
            alvo.setRole(novo);
        }

        Usuario salvo = repo.save(alvo);

        List<Change> changes = diffUsuario(before, salvo);
        String msg = formatUsuarioAtualizadoMensagem(ator, salvo, changes);
        try { notifier.usuarioAtualizado(ator, salvo, msg); }
        catch (Exception e) { log.warn("Falha ao notificar 'usuarioAtualizado' para userId={}: {}", salvo.getId(), e.getMessage()); }

        salvo.setDiasParaExclusao(diasParaExclusaoDo(salvo));
        return toDTO(salvo, ator);
    }

    // =========================================================
    // ALTERAR STATUS
    // =========================================================
    @Transactional
    public UsuarioResponseDTO alterarStatus(Usuario ator, Long id, StatusDTO body) {
        if (ator == null) throw new IllegalStateException("Não autenticado.");

        Usuario alvo = repo.findById(id).orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));

        if (Objects.equals(ator.getId(), alvo.getId()))
            throw new IllegalArgumentException("Você não pode alterar o próprio status.");

        ensureCanManage(ator, alvo, "alterar status");

        if (isBootstrapAdmin(alvo))
            throw new SecurityException("Status do ADMIN principal não pode ser alterado.");

        boolean novoStatus = Boolean.TRUE.equals(body.getAtivo());
        boolean mudou = alvo.isAtivo() != novoStatus;
        if (!mudou) {
            alvo.setDiasParaExclusao(diasParaExclusaoDo(alvo));
            return toDTO(alvo, ator);
        }

        if (!novoStatus) {
            alvo.setAtivo(false);
            alvo.setInativadoEm(LocalDateTime.now(SP));
            alvo.setMustChangePassword(true);
            alvo.setPwdChangeReason("ACCOUNT_INACTIVATED");
            repo.save(alvo);
            try { notifier.usuarioStatusAlterado(ator, alvo, false, "Conta desativada"); }
            catch (Exception e) { log.warn("Falha ao notificar 'usuarioStatusAlterado' (inativo) para userId={}: {}", alvo.getId(), e.getMessage()); }
        } else {
            alvo.setAtivo(true);
            alvo.setInativadoEm(null);
            alvo.setMustChangePassword(false);
            alvo.setPwdChangeReason(null);
            repo.save(alvo);
            try { notifier.usuarioStatusAlterado(ator, alvo, true, "Conta reativada"); }
            catch (Exception e) { log.warn("Falha ao notificar 'usuarioStatusAlterado' (ativo) para userId={}: {}", alvo.getId(), e.getMessage()); }
        }

        alvo.setDiasParaExclusao(diasParaExclusaoDo(alvo));
        return toDTO(alvo, ator);
    }

    // =========================================================
    // RESET DE SENHA
    // =========================================================
    @Transactional
    public Map<String, Object> resetSenha(Usuario ator, Long id) {
        if (ator == null) throw new IllegalStateException("Não autenticado.");

        Usuario alvo = repo.findById(id).orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));
        ensureCanManage(ator, alvo, "resetar a senha");

        if (isBootstrapAdmin(alvo)) throw new SecurityException("Senha do ADMIN principal não pode ser resetada.");

        String nova = gerarSenhaTemporaria(10);
        alvo.setPassword(encoder.encode(nova));
        alvo.setMustChangePassword(true);
        alvo.setPwdChangeReason("RESET_BY_ADMIN");
        alvo.setLastPasswordChangeAt(null);

        repo.save(alvo);

        try { notifier.usuarioSenhaResetada(ator, alvo); }
        catch (Exception e) { log.warn("Falha ao notificar 'usuarioSenhaResetada' para userId={}: {}", alvo.getId(), e.getMessage()); }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mensagem", "Senha redefinida com sucesso.");
        out.put("senhaTemporaria", nova);
        out.put("alvoId", alvo.getId());
        return out;
    }

    // =========================================================
    // EXCLUIR
    // =========================================================
    @Transactional
    public void excluir(Usuario ator, Long id) {
        if (ator == null) throw new IllegalStateException("Não autenticado.");

        Usuario alvo = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));

        if (Objects.equals(ator.getId(), alvo.getId()))
            throw new IllegalArgumentException("Você não pode excluir a própria conta.");

        ensureCanManage(ator, alvo, "excluir");

        if (isBootstrapAdmin(alvo))
            throw new SecurityException("ADMIN principal não pode ser excluído.");

        try { notifier.usuarioExcluido(ator, alvo); }
        catch (Exception e) { log.warn("Falha ao notificar 'usuarioExcluido' para userId={}: {}", alvo.getId(), e.getMessage()); }

        repo.deleteById(id);
    }

    // =========================================================
    // AUTENTICAR / TROCAR SENHA / ATUALIZAR PRÓPRIO
    // =========================================================
    public Usuario autenticar(AuthRequest body) {
        return autenticar(body, null);
    }

    @Transactional
    public Usuario autenticar(AuthRequest body, String ip) {
        if (body == null || isBlank(body.getLogin()) || isBlank(body.getPassword()))
            throw new IllegalArgumentException("Informe login e senha.");

        String key = body.getLogin().trim();
        Optional<Usuario> opt = repo.findByEmailIgnoreCaseOrUsernameIgnoreCaseOrNumero(key, key, key);
        Usuario u = opt.orElseThrow(() -> new NoSuchElementException("Credenciais inválidas."));

        if (!encoder.matches(body.getPassword(), u.getPassword()))
            throw new IllegalArgumentException("Credenciais inválidas.");

        if (!u.isAtivo())
            throw new SecurityException("Usuário inativo.");

        registrarLogin(u, ip);

        return u;
    }

    @Transactional
    public Map<String, Object> trocarSenha(Usuario me, String senhaAtual, String novaSenha) {
        if (me == null) throw new IllegalStateException("Não autenticado.");

        if (isBlank(novaSenha) || !isSimplePassword(novaSenha)) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 5 caracteres e conter ao menos 1 número.");
        }

        boolean forcar = me.isMustChangePassword();
        String reasonBefore = me.getPwdChangeReason();

        // se está forçado a trocar, NÃO pede senha atual
        if (forcar) {
            log.info("[TROCAR_SENHA] Troca forçada - pulando validação de senha atual para userId={}", me.getId());
        } else {
            if (isBlank(senhaAtual) || !encoder.matches(senhaAtual, me.getPassword())) {
                throw new SecurityException("Senha atual inválida.");
            }
            if (encoder.matches(novaSenha, me.getPassword())) {
                throw new IllegalArgumentException("A nova senha não pode ser igual à senha atual.");
            }
        }

        me.setPassword(encoder.encode(novaSenha));
        me.setMustChangePassword(false);
        me.setPwdChangeReason(null);
        me.setLastPasswordChangeAt(LocalDateTime.now(SP));
        repo.save(me);

        if ("FIRST_LOGIN".equalsIgnoreCase(String.valueOf(reasonBefore))) {
            log.info("[TROCAR_SENHA] Notificação de primeiro login disparada para userId={}", me.getId());
            try { notifier.usuarioSenhaAtualizadaNoPrimeiroLogin(me); }
            catch (Exception e) { log.error("[TROCAR_SENHA] Falha ao notificar 'senhaAtualizadaNoPrimeiroLogin' para userId={}: {}", me.getId(), e.getMessage(), e); }
        } else {
            try { notifier.usuarioSenhaAtualizadaPeloProprio(me); }
            catch (Exception e) { log.warn("[TROCAR_SENHA] Falha ao notificar 'usuarioSenhaAtualizadaPeloProprio' para userId={}: {}", me.getId(), e.getMessage()); }
        }

        return Collections.singletonMap("mensagem", "Senha alterada com sucesso.");
    }

    @Transactional
    public UsuarioResponseDTO atualizarProprio(Usuario me, AtualizarProprioUsuarioDTO dto) {
        if (me == null) throw new IllegalStateException("Não autenticado.");

        Usuario before = snapshot(me);

        if (dto.getNome() != null) {
            String nome = dto.getNome().trim();
            if (isBlank(nome)) throw new IllegalArgumentException("Nome é obrigatório.");
            if (nome.length() > 15) throw new IllegalArgumentException("Nome pode ter no máximo 15 caracteres.");
            me.setNome(nome);
        }

        if (dto.getEmail() != null) {
            String email = dto.getEmail().trim();
            if (isBlank(email)) throw new IllegalArgumentException("E-mail é obrigatório.");
            if (repo.existsByEmailAndIdNot(email, me.getId()))
                throw new IllegalArgumentException("E-mail já cadastrado!");
            me.setEmail(email);
        }

        if (dto.getNumero() != null) {
            String numero = dto.getNumero().trim();
            if (numero.isEmpty()) {
                me.setNumero(null);
            } else {
                if (repo.existsByNumeroAndIdNot(numero, me.getId()))
                    throw new IllegalArgumentException("Número já cadastrado!");
                me.setNumero(numero);
            }
        }

        if (dto.getUsername() != null) {
            String user = dto.getUsername().trim();
            if (user.isEmpty()) {
                me.setUsername(null);
            } else {
                if (repo.existsByUsernameAndIdNot(user, me.getId()))
                    throw new IllegalArgumentException("Username já cadastrado!");

                String oldUsername = me.getUsername();
                if (!user.equals(oldUsername)) {
                    validarTrocaUsername(me);
                    me.setUsername(user);
                    registrarTrocaUsername(me, oldUsername, user);
                }
            }
        }

        Usuario saved = repo.save(me);

        List<Change> changes = diffUsuario(before, saved);
        for (Change c : changes) {
            try {
                notifier.usuarioAtualizouProprioPerfil(saved, c.campo, c.de, c.para);
            } catch (Exception e) {
                log.warn("Falha ao notificar 'atualizouProprioPerfil' (campo: {}) para userId={}: {}", c.campo, saved.getId(), e.getMessage());
            }
        }

        saved.setDiasParaExclusao(diasParaExclusaoDo(saved));
        return toDTO(saved, me);
    }

    // =========================================================
    // LOGIN TRACKING
    // =========================================================
    @Transactional
    public void registrarLogin(Usuario u, String ip) {
        if (u == null) return;

        LocalDateTime agora = LocalDateTime.now(SP);
        u.setUltimoLoginEm(agora);
        u.setUltimoLoginIp(safeIp(ip));

        repo.save(u);

        log.info("[LOGIN] userId={} ip={}", u.getId(), u.getUltimoLoginIp());
    }

    private String safeIp(String ip) {
        if (ip == null) return null;
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }

    // =========================================================
    // ONLINE/OFFLINE (✅ NOVO)
    // =========================================================
    public boolean isOnline(Usuario u) {
        if (u == null) return false;
        LocalDateTime last = u.getUltimoAcessoEm();
        if (last == null) return false;
        LocalDateTime agora = LocalDateTime.now(SP);
        long diff = ChronoUnit.SECONDS.between(last, agora);
        return diff >= 0 && diff <= ONLINE_WINDOW_SECONDS;
    }

    @Transactional
    public void registrarAcesso(Usuario u, String ip, String userAgent) {
        if (u == null) return;

        LocalDateTime agora = LocalDateTime.now(SP);

        // Atualiza presença (online/offline)
        u.setUltimoAcessoEm(agora);
        u.setUltimoAcessoIp(safeIp(ip));
        repo.save(u);

        // Salva log do acesso
        try {
            var logEntity = br.com.pousda.pousada.usuarios.domain.UsuarioAcessoLog.of(u, safeIp(ip), userAgent);
            acessoLogRepo.save(logEntity);
        } catch (Exception e) {
            log.warn("[ACESSO_LOG] falha ao salvar log userId={}: {}", u.getId(), e.getMessage());
        }
    }

    // =========================================================
    // USERNAME AUDIT HELPERS
    // =========================================================
    private void validarTrocaUsername(Usuario usuario) {
        LocalDateTime agora = LocalDateTime.now(SP);
        LocalDateTime inicioJanela = agora.minusDays(USERNAME_CHANGE_WINDOW_DAYS);

        long qtd = usernameAuditRepo.countByUsuarioIdAndChangedAtAfter(usuario.getId(), inicioJanela);
        if (qtd >= USERNAME_CHANGE_LIMIT) {
            UsuarioUsernameAudit ultima = usernameAuditRepo
                    .findTopByUsuarioIdOrderByChangedAtDesc(usuario.getId())
                    .orElse(null);

            if (ultima != null) {
                LocalDateTime liberaEm = ultima.getChangedAt().plusDays(USERNAME_CHANGE_WINDOW_DAYS);
                long diasRestantes = ChronoUnit.DAYS.between(agora.toLocalDate(), liberaEm.toLocalDate());
                if (diasRestantes < 0) diasRestantes = 0;

                throw new IllegalArgumentException(
                        "Você já alterou o username " + USERNAME_CHANGE_LIMIT +
                                " vezes nos últimos 30 dias. Tente novamente em " + diasRestantes + " dia(s)."
                );
            } else {
                throw new IllegalArgumentException("Você já atingiu o limite de alterações de username nos últimos 30 dias.");
            }
        }
    }

    private void registrarTrocaUsername(Usuario usuario, String oldUsername, String newUsername) {
        if (newUsername == null || newUsername.equals(oldUsername)) return;

        UsuarioUsernameAudit audit = new UsuarioUsernameAudit();
        audit.setUsuario(usuario);
        audit.setOldUsername(oldUsername);
        audit.setNewUsername(newUsername);
        audit.setChangedAt(LocalDateTime.now(SP));

        usernameAuditRepo.save(audit);
    }

    // =========================================================
    // DIFF / FORMAT HELPERS
    // =========================================================
    private static class Change {
        final String campo;
        final String de;
        final String para;
        Change(String campo, String de, String para) {
            this.campo = campo;
            this.de = de;
            this.para = para;
        }
    }

    private Usuario snapshot(Usuario u) {
        Usuario s = new Usuario();
        s.setId(u.getId());
        s.setNome(u.getNome());
        s.setEmail(u.getEmail());
        s.setNumero(u.getNumero());
        s.setUsername(u.getUsername());
        s.setRole(u.getRole());
        s.setAtivo(u.isAtivo());
        return s;
    }

    private List<Change> diffUsuario(Usuario before, Usuario after) {
        List<Change> out = new ArrayList<>();
        addIfChanged(out, "Nome", before.getNome(), after.getNome());
        addIfChanged(out, "E-mail", before.getEmail(), after.getEmail());
        addIfChanged(out, "Número", before.getNumero(), after.getNumero());
        addIfChanged(out, "Username", before.getUsername(), after.getUsername());
        addIfChanged(out, "Perfil", before.getRole(), after.getRole());
        return out;
    }

    private void addIfChanged(List<Change> list, String campo, String a, String b) {
        String va = norm(a), vb = norm(b);
        if (!Objects.equals(va, vb)) {
            list.add(new Change(campo, show(va), show(vb)));
        }
    }

    private String norm(String s) { return (s == null || s.trim().isEmpty()) ? null : s.trim(); }
    private String show(String s) { return (s == null) ? "(vazio)" : s; }

    private String formatUsuarioAtualizadoMensagem(Usuario ator, Usuario alvo, List<Change> changes) {
        String actor = (ator != null)
                ? (safe(ator.getNome()) + " (id=" + ator.getId() + ")")
                : "(desconhecido)";

        String target = safe(alvo.getNome()) + " (id=" + alvo.getId() + ")";

        if (changes.isEmpty()) {
            return "Usuário atualizado por " + actor + ". Alvo: " + target + ". Nenhuma alteração de dados detectada.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Usuário atualizado por ").append(actor).append(".\n");
        sb.append("Alvo: ").append(target).append(".\n");
        sb.append("Mudanças:\n");
        for (Change c : changes) {
            sb.append("• ").append(c.campo).append(": ")
                    .append(c.de).append(" → ").append(c.para).append("\n");
        }
        return sb.toString().trim();
    }

    private String safe(String s) { return isBlank(s) ? "(sem nome)" : s; }

    // =========================================================
    // VALIDAÇÕES / REGRAS EXISTENTES
    // =========================================================
    private void validarCriacao(CriarUsuarioDTO dto) {
        if (dto == null) throw new IllegalArgumentException("Dados obrigatórios não informados.");

        String nome = isBlank(dto.getNome()) ? null : dto.getNome().trim();
        String email = isBlank(dto.getEmail()) ? null : dto.getEmail().trim();
        String user = isBlank(dto.getUsername()) ? null : dto.getUsername().trim();
        String numero = isBlank(dto.getNumero()) ? null : dto.getNumero().trim();

        if (nome == null) throw new IllegalArgumentException("Nome é obrigatório.");
        if (nome.length() > 15) throw new IllegalArgumentException("Nome pode ter no máximo 15 caracteres.");

        if (email == null) throw new IllegalArgumentException("E-mail é obrigatório.");
        if (repo.existsByEmail(email)) throw new IllegalArgumentException("E-mail já cadastrado!");

        if (user != null && repo.existsByUsername(user)) throw new IllegalArgumentException("Username já cadastrado!");
        if (numero != null && repo.existsByNumero(numero)) throw new IllegalArgumentException("Número já cadastrado!");
    }

    private boolean isDev(Usuario u) { return u != null && ROLE_DEV.equals(u.getRole()); }
    private boolean isAdmin(Usuario u) { return u != null && ROLE_ADMIN.equals(u.getRole()); }
    private boolean isGerente(Usuario u) { return u != null && ROLE_GERENTE.equals(u.getRole()); }

    private void ensureCanManage(Usuario ator, Usuario alvo, String acao) {
        if (ator == null || alvo == null) throw new SecurityException("Operação não permitida.");
        boolean ok;
        if (isDev(ator)) {
            ok = !isDev(alvo);
        } else if (isAdmin(ator)) {
            ok = isGerente(alvo);
        } else {
            ok = false;
        }
        if (!ok) throw new SecurityException("Você não tem permissão para " + acao + " este usuário.");
    }

    private boolean canChangeRole(Usuario ator, Usuario alvo, String novoRole) {
        if (isDev(ator) && !isDev(alvo)) {
            return ROLE_ADMIN.equals(novoRole) || ROLE_GERENTE.equals(novoRole);
        }
        if (isAdmin(ator)) {
            return isGerente(alvo) && ROLE_GERENTE.equals(novoRole);
        }
        return false;
    }

    private String roleAlvo(String role) {
        if (isBlank(role)) return ROLE_GERENTE;
        String r = role.trim().toUpperCase(Locale.ROOT);
        if (ROLE_ADMIN.equals(r)) return ROLE_ADMIN;
        if (ROLE_GERENTE.equals(r)) return ROLE_GERENTE;
        throw new IllegalArgumentException("Perfil inválido.");
    }

    private String gerarSenhaTemporaria(int length) {
        final String CHAR_LOWER = "abcdefghijklmnopqrstuvwxyz";
        final String CHAR_UPPER = CHAR_LOWER.toUpperCase();
        final String NUMBER = "0123456789";
        final String DATA_FOR_RANDOM = CHAR_LOWER + CHAR_UPPER + NUMBER;

        StringBuilder sb = new StringBuilder(length);
        sb.append(NUMBER.charAt(RANDOM.nextInt(NUMBER.length())));
        for (int i = 1; i < length; i++) {
            sb.append(DATA_FOR_RANDOM.charAt(RANDOM.nextInt(DATA_FOR_RANDOM.length())));
        }
        return sb.toString();
    }

    private boolean isSimplePassword(String s) {
        if (s == null || s.length() < 5) return false;
        char[] arr = s.toCharArray();
        for (char c : arr) {
            if (Character.isDigit(c)) return true;
        }
        return false;
    }

    private boolean isBootstrapAdmin(Usuario u) { return u != null && u.isBootstrapAdmin(); }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /** D-7 para exclusão automática após inativação. */
    public Integer diasParaExclusaoDo(Usuario u) {
        if (u == null || u.isAtivo() || u.getInativadoEm() == null) return null;
        LocalDate inativadoDia = u.getInativadoEm().atZone(SP).toLocalDate();
        long passados = ChronoUnit.DAYS.between(inativadoDia, LocalDate.now(SP));
        int faltam = 7 - (int) passados;
        return Math.max(faltam, 0);
    }

    // =========================================================
    // AVATAR
    // =========================================================
    @Transactional
    public Usuario atualizarAvatarPhotoBase64(Usuario ator, Long alvoId, String dataUrl) {
        if (ator == null) throw new IllegalStateException("Não autenticado.");

        Usuario alvo = repo.findById(alvoId)
                .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));

        boolean isSelf = Objects.equals(ator.getId(), alvo.getId());
        if (!isSelf) {
            ensureCanManage(ator, alvo, "editar avatar");
        }

        long t0 = System.currentTimeMillis();
        int inLen = dataUrl != null ? dataUrl.length() : 0;

        try {
            String normalized = processDataUrlToJpeg256(dataUrl);

            alvo.setAvatarBase64(normalized);
            alvo.setAvatarMode(AvatarMode.PHOTO);
            alvo.setAvatarColor(null);
            alvo.setAvatarGradient(null);
            alvo.setAvatarVersion(Optional.ofNullable(alvo.getAvatarVersion()).orElse(0L) + 1);

            Usuario saved = repo.save(alvo);

            long took = System.currentTimeMillis() - t0;
            log.info("[AVATAR/SVC] OK actorId={} targetId={} self={} in.len={} out.len={} version={} tookMs={}",
                    ator.getId(), alvoId, isSelf, inLen, normalized.length(), saved.getAvatarVersion(), took);

            return saved;

        } catch (IllegalArgumentException e) {
            long took = System.currentTimeMillis() - t0;
            log.warn("[AVATAR/SVC] FAIL_VALIDATION actorId={} targetId={} self={} in.len={} msg='{}' tookMs={}",
                    ator.getId(), alvoId, isSelf, inLen, e.getMessage(), took);
            throw e;
        } catch (Exception e) {
            long took = System.currentTimeMillis() - t0;
            log.error("[AVATAR/SVC] FAIL actorId={} targetId={} self={} in.len={} tookMs={}",
                    ator.getId(), alvoId, isSelf, inLen, took, e);
            throw e;
        }
    }

    private String processDataUrlToJpeg256(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:image/")) {
            throw new IllegalArgumentException("Formato inválido. Envie dataURL de image/png ou image/jpeg.");
        }

        String lower = dataUrl.toLowerCase(Locale.ROOT);
        boolean isPng = lower.startsWith("data:image/png;base64,");
        boolean isJpg = lower.startsWith("data:image/jpeg;base64,") || lower.startsWith("data:image/jpg;base64,");
        if (!isPng && !isJpg) {
            throw new IllegalArgumentException("Apenas PNG ou JPEG são suportados.");
        }

        String base64 = dataUrl.substring(dataUrl.indexOf(",") + 1);
        byte[] bytes = Base64.getDecoder().decode(base64);
        if (bytes.length > 3_000_000) {
            throw new IllegalArgumentException("Imagem muito grande (máx. 3MB).");
        }

        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) {
                throw new IllegalArgumentException("Imagem inválida.");
            }

            int w = src.getWidth();
            int h = src.getHeight();
            int size = Math.min(w, h);

            int x = (w - size) / 2;
            int y = (h - size) / 2;

            int target = 256;
            BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);

            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // recorta quadrado e redimensiona
            BufferedImage cropped = src.getSubimage(x, y, size, size);
            g2.drawImage(cropped, 0, 0, target, target, null);
            g2.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "jpeg", baos);
            byte[] jpeg = baos.toByteArray();

            String outDataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
            if (outDataUrl.length() > 800_000) {
                throw new IllegalArgumentException("Avatar resultou maior que o permitido.");
            }

            return outDataUrl;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AVATAR/PROC] exception ao processar imagem", e);
            throw new IllegalArgumentException("Falha ao processar imagem.");
        }
    }

    public AvatarViewDTO obterAvatarDe(Usuario req, Long targetId) {
        if (req == null) throw new IllegalStateException("Não autenticado.");

        Usuario alvo = repo.findById(targetId)
                .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));

        boolean podeVer = Objects.equals(req.getId(), alvo.getId()) || isDev(req) || isAdmin(req);
        if (!podeVer) {
            throw new SecurityException("Você não tem permissão para visualizar este avatar.");
        }

        AvatarMode mode = Optional.ofNullable(alvo.getAvatarMode()).orElse(AvatarMode.COLOR);
        String color = alvo.getAvatarColor();
        String gradient = alvo.getAvatarGradient();
        String data = alvo.getAvatarBase64();
        Long version = Optional.ofNullable(alvo.getAvatarVersion()).orElse(0L);

        if (mode == AvatarMode.PHOTO) {
            color = null;
            gradient = null;
        } else {
            data = null;
            if (color == null && gradient == null) {
                color = "#4F46E5";
            }
        }

        return new AvatarViewDTO(mode.name(), color, gradient, data, version);
    }

    @Transactional
    public Usuario atualizarAvatarPrefs(Usuario usuario, String mode, String color, String gradient) {
        if (usuario == null) throw new IllegalStateException("Usuário inválido.");
        if (mode == null) throw new IllegalArgumentException("Modo de avatar não informado.");

        String up = mode.toUpperCase(Locale.ROOT);
        if ("COLOR".equals(up)) {
            if (color == null || !color.matches("^#([A-Fa-f0-9]{6})$")) {
                throw new IllegalArgumentException("Cor inválida.");
            }
            usuario.setAvatarMode(AvatarMode.COLOR);
            usuario.setAvatarColor(color);
            usuario.setAvatarGradient(null);
            usuario.setAvatarBase64(null);
        } else if ("PHOTO".equals(up)) {
            usuario.setAvatarMode(AvatarMode.PHOTO);
            usuario.setAvatarColor(null);
            usuario.setAvatarGradient(null);
        } else {
            throw new IllegalArgumentException("Modo de avatar desconhecido: " + mode);
        }

        usuario.setAvatarVersion(
                Optional.ofNullable(usuario.getAvatarVersion()).orElse(0L) + 1
        );

        return repo.save(usuario);
    }

    // =========================================================
    // TEMA
    // =========================================================
    @Transactional
    public Usuario atualizarTema(Long id, String tema) {
        Usuario usuario = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        if (tema == null) throw new IllegalArgumentException("Tema não informado.");
        String t = tema.trim().toLowerCase(Locale.ROOT);
        if (!"claro".equals(t) && !"escuro".equals(t)) {
            throw new IllegalArgumentException("Tema inválido. Use 'claro' ou 'escuro'.");
        }

        usuario.setTema(t);
        return repo.save(usuario);
    }

    // =========================================================
    // MAPEAMENTO PARA DTO (com online ✅)
    // =========================================================
    public UsuarioResponseDTO toDTO(Usuario u) {
        return toDTO(u, null);
    }

    public UsuarioResponseDTO toDTO(Usuario alvo, Usuario ator) {

        Long criadoPorId = null;
        if (alvo.getCriadoPor() != null) {
            try {
                if (Hibernate.isInitialized(alvo.getCriadoPor())) {
                    criadoPorId = alvo.getCriadoPor().getId();
                } else {
                    criadoPorId = alvo.getCriadoPor().getId();
                }
            } catch (Exception ignored) {}
        }

        String avatarModeStr = (alvo.getAvatarMode() != null) ? alvo.getAvatarMode().name() : null;

        // 🔒 PERMISSÃO: somente DEV ou ADMIN podem ver dados sensíveis de login
        boolean podeVerLogin =
                ator != null &&
                        ("DEV".equals(ator.getRole()) || "ADMIN".equals(ator.getRole()));

        LocalDateTime ultimoLoginEm = podeVerLogin ? alvo.getUltimoLoginEm() : null;
        String ultimoLoginIp = podeVerLogin ? alvo.getUltimoLoginIp() : null;

        UsuarioResponseDTO dto = new UsuarioResponseDTO(
                alvo.getId(),
                alvo.getCodigo(),
                alvo.getUsername(),
                alvo.getEmail(),
                alvo.getNumero(),
                alvo.getNome(),
                alvo.getRole(),
                alvo.isAtivo(),
                alvo.isBootstrapAdmin(),
                alvo.getCriadoEm(),
                alvo.isMustChangePassword(),
                alvo.getPwdChangeReason(),
                alvo.getLastPasswordChangeAt(),
                alvo.getInativadoEm(),
                avatarModeStr,
                alvo.getAvatarColor(),
                alvo.getAvatarGradient(),
                alvo.getAvatarVersion(),
                alvo.getTema(),
                criadoPorId,
                alvo.getCriadoPorNome(),
                alvo.getDiasParaExclusao(),
                ultimoLoginEm,
                ultimoLoginIp
        );

        // ✅ online/offline aqui (não muda construtor atual)
        try {
            dto.setOnline(isOnline(alvo)); // precisa existir o campo + setter no DTO
        } catch (Exception ignored) {}

        return dto;
    }

    public UsuarioResponseDTO buscarPorIdDTO(Long id) {
        Usuario u = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));

        u.setDiasParaExclusao(diasParaExclusaoDo(u));
        return toDTO(u, null);
    }

    // ✅ overload (pra controller /{id} e pra /me)
    public UsuarioResponseDTO buscarPorIdDTO(Long id, Usuario ator) {
        Usuario u = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));

        u.setDiasParaExclusao(diasParaExclusaoDo(u));
        return toDTO(u, ator);
    }
}
