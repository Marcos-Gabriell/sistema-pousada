package br.com.pousda.pousada.financeiro.application;

import br.com.pousda.pousada.exception.HospedagemNaoEncontradaException;
import br.com.pousda.pousada.exception.OperacaoNaoPermitidaException;
import br.com.pousda.pousada.exception.ValorInvalidoException;
import br.com.pousda.pousada.financeiro.domain.LancamentoFinanceiro;
import br.com.pousda.pousada.financeiro.domain.enuns.OrigemLancamento;
import br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento;
import br.com.pousda.pousada.financeiro.dtos.LancamentoDTO;
import br.com.pousda.pousada.financeiro.dtos.LancamentoResponseDTO;
import br.com.pousda.pousada.financeiro.infra.LancamentoFinanceiroRepository;
import br.com.pousda.pousada.hospedagens.domain.Hospedagem;
import br.com.pousda.pousada.hospedagens.domain.enuns.TipoHospedagem;
import br.com.pousda.pousada.hospedagens.infra.HospedagemRepository;
import br.com.pousda.pousada.notificacoes.application.UsersQueryPort;
import br.com.pousda.pousada.notificacoes.application.facade.NotifierFacade;
import br.com.pousda.pousada.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceiroService {

    private static final Logger log = LoggerFactory.getLogger(FinanceiroService.class);

    private final LancamentoFinanceiroRepository repo;
    private final NotifierFacade notifier;
    private final UsersQueryPort users;
    private final HospedagemRepository hospedagens;

    private static final ZoneId ZONE_BR = ZoneId.of("America/Bahia");

    /* ====================== Auth helpers ====================== */

    private AuthPrincipal principal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthPrincipal) {
            return (AuthPrincipal) a.getPrincipal();
        }
        return null;
    }

    private Long uid() {
        AuthPrincipal p = principal();
        if (p != null && p.getId() != null) {
            return p.getId();
        }
        try {
            long id = users.currentUserId();
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String uname() {
        AuthPrincipal p = principal();
        if (p != null && p.getName() != null && !p.getName().isBlank()) return p.getName();
        if (p != null && p.getUsername() != null && !p.getUsername().isBlank()) return p.getUsername();
        if (p != null && p.getEmail() != null && !p.getEmail().isBlank()) return p.getEmail();
        return "-";
    }

    private String displayUser() {
        Long id = uid();
        String nome = uname();
        return (nome != null ? nome : "-") + " | " + (id != null ? id : "-");
    }

    private boolean isAdmin(Long id) {
        Set<Long> s = users.adminIds();
        return id != null && s != null && s.contains(id);
    }

    private boolean isDev(Long id) {
        Set<Long> s = users.devIds();
        return id != null && s != null && s.contains(id);
    }

    private boolean isGerente(Long id) {
        Set<Long> s = users.gerenteIds();
        return id != null && s != null && s.contains(id);
    }

    private boolean podeCriar() {
        Long id = uid();
        return isAdmin(id) || isGerente(id) || isDev(id);
    }

    private boolean podeEditarOuCancelar() {
        Long id = uid();
        return isAdmin(id) || isDev(id);
    }

    private String perfilAtual() {
        Long id = uid();
        if (isAdmin(id)) return "ADMIN";
        if (isDev(id)) return "DEV";
        if (isGerente(id)) return "GERENTE";
        return "-";
    }

    private boolean isCancelado(LancamentoFinanceiro l) {
        return l.getExcluidoEm() != null;
    }

    /* ========== código mensal yyyymm + seq(001..) ========== */

    private String gerarCodigoDoMes(LocalDate baseDate) {
        LocalDate ref = (baseDate != null ? baseDate : LocalDate.now(ZONE_BR));
        YearMonth ym = YearMonth.from(ref);
        String prefix = String.format("%04d%02d", ym.getYear(), ym.getMonthValue()); // ex: 202511

        return repo.findTopByCodigoStartingWithOrderByCodigoDesc(prefix)
                .map(ult -> {
                    String codigo = ult.getCodigo();
                    if (codigo == null || codigo.length() <= prefix.length()) {
                        return prefix + "001";
                    }
                    String sufix = codigo.substring(prefix.length());
                    int next;
                    try {
                        next = Integer.parseInt(sufix) + 1;
                    } catch (NumberFormatException e) {
                        next = 1;
                    }
                    return prefix + String.format("%03d", next);
                })
                .orElse(prefix + "001");
    }

    private <T> T salvarComRetry(java.util.function.Supplier<T> action) {
        return action.get();
    }

    /* ============================ CREATE ============================ */

    @Transactional
    public LancamentoResponseDTO criarManual(LancamentoDTO dto) {
        if (!podeCriar()) {
            throw new OperacaoNaoPermitidaException("Sem permissão para lançar financeiro.");
        }
        if (dto.getTipo() == null) {
            throw new ValorInvalidoException("Tipo é obrigatório.");
        }
        if (dto.getValor() == null || dto.getValor() <= 0) {
            throw new ValorInvalidoException("Valor deve ser > 0.");
        }

        if (dto.getOrigem() == null) {
            dto.setOrigem(OrigemLancamento.MANUAL);
        }

        LocalDate hoje = LocalDate.now(ZONE_BR);
        LocalDate dataEfetiva = dto.getData() != null ? dto.getData() : hoje;

        LancamentoFinanceiro novoLancamento = new LancamentoFinanceiro();
        novoLancamento.setCodigo(gerarCodigoDoMes(dataEfetiva));
        novoLancamento.setTipo(dto.getTipo());
        novoLancamento.setOrigem(dto.getOrigem());
        novoLancamento.setReferenciaId(dto.getReferenciaId());
        novoLancamento.setData(dataEfetiva);
        novoLancamento.setDataHora(LocalDateTime.now(ZONE_BR));
        novoLancamento.setValor(dto.getValor());
        novoLancamento.setFormaPagamento(blankToNull(dto.getFormaPagamento()));
        novoLancamento.setDescricao(trimOrNull(dto.getDescricao()));
        novoLancamento.setCriadoPorId(uid());
        novoLancamento.setCriadoPorNome(displayUser());
        novoLancamento.setPrefeitura(Boolean.FALSE);

        LancamentoFinanceiro salvo;
        try {
            salvo = salvarComRetry(() -> repo.save(novoLancamento));
        } catch (DataIntegrityViolationException e) {
            throw e;
        }

        notifier.finLancamentoCriado(
                salvo.getId(),
                salvo.getTipo().name(),
                salvo.getOrigem().name(),
                salvo.getValor(),
                salvo.getDescricao(),
                uid(),
                currentUserJson(),
                destinatariosOperacionaisComAutor()
        );

        return toResponse(salvo);
    }

    /* ============================= READ ============================= */

    @Transactional(readOnly = true)
    public List<LancamentoResponseDTO> listar(TipoLancamento tipo,
                                              OrigemLancamento origem,
                                              LocalDate ini,
                                              LocalDate fim) {

        List<LancamentoFinanceiro> base;

        if (ini != null && fim != null) {
            base = repo.findByDataBetweenAndExcluidoEmIsNull(ini, fim);
        } else if (ini != null) {
            base = repo.findByDataGreaterThanEqualAndExcluidoEmIsNull(ini);
        } else if (fim != null) {
            base = repo.findByDataLessThanEqualAndExcluidoEmIsNull(fim);
        } else {
            base = repo.findByExcluidoEmIsNullOrderByDataHoraDesc();
        }

        return base.stream()
                .filter(l -> tipo == null   || l.getTipo()   == tipo)
                .filter(l -> origem == null || l.getOrigem() == origem)
                .sorted(
                        Comparator
                                .comparing(LancamentoFinanceiro::getData,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(LancamentoFinanceiro::getId,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                )
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Double saldoAtual() {
        return repo.calcularSaldoAtual();
    }

    @Transactional(readOnly = true)
    public Double saldoAtualPrefeitura() {
        return repo.calcularSaldoAtualPrefeitura();
    }

    /* ============================ UPDATE ============================ */

    @Transactional
    public LancamentoResponseDTO atualizar(Long id, LancamentoDTO dto) {
        if (!podeEditarOuCancelar()) {
            throw new OperacaoNaoPermitidaException("Somente ADMIN ou DEV podem editar.");
        }

        LancamentoFinanceiro l = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lançamento não encontrado"));

        if (isCancelado(l)) {
            throw new OperacaoNaoPermitidaException("Lançamento cancelado não pode ser editado.");
        }

        Integer ed = l.getEdicoes() != null ? l.getEdicoes() : 0;
        if (ed >= 3) {
            throw new OperacaoNaoPermitidaException("Limite de 3 edições atingido para este lançamento.");
        }

        String deDesc = l.getDescricao();
        Double deVal = l.getValor();

        if (dto.getValor() != null && dto.getValor() > 0) {
            l.setValor(dto.getValor());
        }
        if (dto.getDescricao() != null) {
            l.setDescricao(trimOrNull(dto.getDescricao()));
        }
        if (dto.getFormaPagamento() != null) {
            l.setFormaPagamento(blankToNull(dto.getFormaPagamento()));
        }

        l.setEdicoes(ed + 1);
        l.setEditadoEm(LocalDateTime.now(ZONE_BR));
        l.setEditadoPorId(uid());
        l.setEditadoPorNome(displayUser());

        LancamentoFinanceiro up = repo.save(l);

        notifier.finLancamentoAtualizado(
                up.getId(),
                up.getTipo().name(),
                up.getOrigem().name(),
                deDesc, deVal,
                up.getDescricao(), up.getValor(),
                uid(),
                currentUserJson(),
                destinatariosOperacionaisComAutor()
        );

        return toResponse(up);
    }

    /* ============================ CANCEL ============================ */

    @Transactional
    public void cancelar(Long id, String motivo) {
        if (!podeEditarOuCancelar()) {
            throw new OperacaoNaoPermitidaException("Somente ADMIN ou DEV podem cancelar.");
        }

        LancamentoFinanceiro l = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lançamento não encontrado"));

        if (isCancelado(l)) {
            log.warn("Lançamento {} já estava cancelado.", l.getCodigo());
            return;
        }

        final String perfil = perfilAtual();

        l.setExcluidoEm(LocalDateTime.now(ZONE_BR));
        l.setExcluidoPorId(uid());
        l.setExcluidoPorNome(displayUser());
        l.setExcluidoMotivo(
                (motivo == null || motivo.isBlank())
                        ? "Cancelado manualmente"
                        : motivo.trim()
        );

        repo.save(l);

        log.info("✅ Lançamento {} cancelado. Dashboard será atualizado.", l.getCodigo());

        notifier.finLancamentoCancelado(
                l.getId(),
                l.getTipo().name(),
                l.getOrigem().name(),
                l.getValor(),
                l.getExcluidoMotivo(),
                perfil,
                uid(),
                currentUserJson(),
                destinatariosOperacionaisComAutor()
        );
    }

    /* ===== Integração com Hospedagem (entrada automática) ===== */

    // ===== Integração com Hospedagem (entrada automática) =====
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarEntradaHospedagem(Long hospedagemId,
                                           Double valor,
                                           String formaPgto,
                                           String codigoHosp,
                                           boolean prefeitura) {

        if (hospedagemId == null || valor == null || valor <= 0) {
            return;
        }

        // evita duplicar lançamento para a mesma hospedagem
        if (repo.existsByOrigemAndReferenciaIdAndExcluidoEmIsNull(OrigemLancamento.HOSPEDAGEM, hospedagemId)) {
            log.warn("Entrada financeira já existe para hospedagem {}. Ignorando novo lançamento.", hospedagemId);
            return;
        }

        LocalDate hoje = LocalDate.now(ZONE_BR);

        LancamentoFinanceiro l = new LancamentoFinanceiro();
        l.setCodigo(gerarCodigoDoMes(hoje));
        l.setTipo(TipoLancamento.ENTRADA);
        l.setOrigem(OrigemLancamento.HOSPEDAGEM); // <-- só HOSPEDAGEM, como no seu enum
        l.setReferenciaId(hospedagemId);
        l.setData(hoje);
        l.setDataHora(LocalDateTime.now(ZONE_BR));
        l.setValor(valor);
        l.setFormaPagamento(blankToNull(formaPgto));
        l.setDescricao("Entrada hospedagem " +
                (codigoHosp != null && !codigoHosp.isBlank() ? "#" + codigoHosp.trim() : ""));
        l.setCriadoPorId(uid());
        l.setCriadoPorNome(displayUser());
        // prefeitura = true/false vem da hospedagem (TipoHospedagem) lá no service de Hospedagem
        l.setPrefeitura(prefeitura);

        try {
            salvarComRetry(() -> repo.save(l));

            log.info("💰 Entrada financeira criada para hospedagem: {}", codigoHosp);

            notifier.finLancamentoCriado(
                    l.getId(),
                    "ENTRADA",
                    "HOSPEDAGEM",
                    valor,
                    l.getDescricao(),
                    uid(),
                    currentUserJson(),
                    destinatariosOperacionaisComAutor()
            );
        } catch (DataIntegrityViolationException e) {
            // não deixa essa exception sujar a transação da hospedagem
            log.error("[FIN] Código duplicado ao registrar entrada da hospedagem {}: {}", codigoHosp, e.getMessage());
        } catch (Exception e) {
            log.error("[FIN] Falha ao registrar entrada (via hospedagem {}): {}", codigoHosp, e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelarEntradaDeHospedagem(Long hospedagemId, String motivo) {

        List<LancamentoFinanceiro> lancs =
                repo.findByReferenciaIdAndExcluidoEmIsNull(hospedagemId);

        if (lancs.isEmpty()) {
            log.warn("Nenhuma entrada financeira ativa encontrada para hospedagem: {}", hospedagemId);
            return;
        }

        final String perfil = perfilAtual();

        for (LancamentoFinanceiro l : lancs) {
            if (isCancelado(l)) {
                continue;
            }

            l.setExcluidoEm(LocalDateTime.now(ZONE_BR));
            l.setExcluidoPorId(uid());
            l.setExcluidoPorNome(displayUser());
            l.setExcluidoMotivo(
                    (motivo == null || motivo.isBlank())
                            ? "Hospedagem cancelada/excluída"
                            : motivo.trim()
            );
            repo.save(l);

            log.info("✅ Entrada financeira cancelada para hospedagem: {} (lançamento {})",
                    hospedagemId, l.getCodigo());

            notifier.finLancamentoCancelado(
                    l.getId(),
                    "ENTRADA",
                    l.getOrigem().name(),
                    l.getValor(),
                    l.getExcluidoMotivo(),
                    perfil,
                    uid(),
                    currentUserJson(),
                    destinatariosOperacionaisComAutor()
            );
        }
    }

    /* ======================== Notifier helpers ======================== */

    private String currentUserJson() {
        Long id = uid();
        String nome = uname();
        String display = displayUser();
        return "{\"id\":" + (id != null ? id : null)
                + ",\"nome\":\"" + (nome != null ? nome : "-")
                + "\",\"display\":\"" + display + "\"}";
    }

    private java.util.Set<Long> destinatariosOperacionaisComAutor() {
        Long autorId = uid();
        java.util.Set<Long> admins   = users != null ? users.adminIds()   : java.util.Set.of();
        java.util.Set<Long> devs     = users != null ? users.devIds()     : java.util.Set.of();
        java.util.Set<Long> gerentes = users != null ? users.gerenteIds() : java.util.Set.of();

        java.util.HashSet<Long> out = new java.util.HashSet<>();
        if (admins != null)   out.addAll(admins);
        if (devs != null)     out.addAll(devs);
        if (gerentes != null) out.addAll(gerentes);
        if (autorId != null)  out.add(autorId);

        return out;
    }

    /* ============================ Mappers ============================ */

    private LancamentoResponseDTO toResponse(LancamentoFinanceiro l) {
        boolean cancelado = l.getExcluidoEm() != null;

        return LancamentoResponseDTO.builder()
                .id(l.getId())
                .codigo(l.getCodigo())
                .tipo(l.getTipo())
                .origem(l.getOrigem())
                .referenciaId(l.getReferenciaId())
                .data(l.getData())
                .dataHora(l.getDataHora())
                .valor(l.getValor())
                .formaPagamento(l.getFormaPagamento())
                .descricao(l.getDescricao())
                .criadoPorId(l.getCriadoPorId())
                .criadoPorNome(l.getCriadoPorNome())
                .edicoes(l.getEdicoes())
                .editadoEm(l.getEditadoEm())
                .editadoPorId(l.getEditadoPorId())
                .editadoPorNome(l.getEditadoPorNome())
                .cancelado(cancelado)
                .canceladoEm(l.getExcluidoEm())
                .canceladoPorId(l.getExcluidoPorId())
                .canceladoPorNome(l.getExcluidoPorNome())
                .canceladoPorPerfil(null)
                .canceladoMotivo(l.getExcluidoMotivo())
                .build();
    }

    /* ============================ Utils ============================= */

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
