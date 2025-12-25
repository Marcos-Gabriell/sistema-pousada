package br.com.pousda.pousada.hospedagens.application;

import br.com.pousda.pousada.exception.*;
import br.com.pousda.pousada.financeiro.application.FinanceiroService;
import br.com.pousda.pousada.hospedagens.domain.Hospedagem;
import br.com.pousda.pousada.hospedagens.domain.enuns.TipoHospedagem;
import br.com.pousda.pousada.hospedagens.dtos.CheckoutDTO;
import br.com.pousda.pousada.hospedagens.dtos.HospedagemDTO;
import br.com.pousda.pousada.hospedagens.dtos.HospedagemResponseDTO;
import br.com.pousda.pousada.hospedagens.infra.HospedagemRepository;
import br.com.pousda.pousada.notificacoes.application.facade.NotifierFacade;
import br.com.pousda.pousada.quartos.domain.Quarto;
import br.com.pousda.pousada.quartos.domain.enuns.StatusQuarto;
import br.com.pousda.pousada.quartos.infra.QuartoRepository;
import br.com.pousda.pousada.reservas.domain.Reserva;
import br.com.pousda.pousada.reservas.domain.StatusReserva;
import br.com.pousda.pousada.reservas.infra.ReservaRepository;
import br.com.pousda.pousada.usuarios.domain.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class HospedagemService {

    @Autowired private HospedagemRepository hospedagemRepository;
    @Autowired private QuartoRepository quartoRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private NotifierFacade notifier;
    @Autowired private FinanceiroService financeiro;

    private static final ZoneId ZONE_BR = ZoneId.of("America/Bahia");

    /* ============================ Helpers ============================ */

    private LocalDate todayBr() { return LocalDate.now(ZONE_BR); }
    private LocalDateTime nowBr() { return LocalDateTime.now(ZONE_BR); }

    private Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Usuario usuarioAtualEntity() {
        Authentication a = auth();
        if (a == null || !a.isAuthenticated()) return null;
        Object p = a.getPrincipal();
        if (p instanceof Usuario) {
            return (Usuario) p;
        }
        return null;
    }

    private String usuarioAtualOuSystem() {
        Authentication a = auth();
        if (a == null || !a.isAuthenticated()) return "system";

        Object p = a.getPrincipal();
        if (p instanceof Usuario) {
            Usuario u = (Usuario) p;
            return u.getEmail() != null ? u.getEmail() : String.valueOf(u.getId());
        }

        if (p instanceof UserDetails) return ((UserDetails) p).getUsername();
        return p != null ? p.toString() : "system";
    }

    private Long usuarioAtualIdOrNull() {
        Usuario u = usuarioAtualEntity();
        return u != null ? u.getId() : null;
    }

    private String usuarioAtualNomeOrNull() {
        Usuario u = usuarioAtualEntity();
        return u != null ? u.getNome() : null;
    }

    private boolean hasAnyRole(String... roles) {
        Authentication a = auth();
        if (a == null || a.getAuthorities() == null) return false;

        java.util.Set<String> wanted = java.util.Arrays.stream(roles)
                .filter(Objects::nonNull)
                .flatMap(r -> {
                    String up = r.toUpperCase();
                    return java.util.stream.Stream.of(up, "ROLE_" + up);
                })
                .collect(java.util.stream.Collectors.toSet());

        for (GrantedAuthority g : a.getAuthorities()) {
            String auth = String.valueOf(g.getAuthority()).toUpperCase();
            if (wanted.contains(auth)) return true;
        }
        return false;
    }

    private void safeNotify(Runnable r, String ctx) {
        try { if (r != null) r.run(); }
        catch (Exception e) { System.err.println("[NOTIFY-FAIL] " + ctx + ": " + e.getMessage()); }
    }

    private String gerarCodigoSequencialMensal() {
        LocalDate hoje = todayBr();
        String prefix = String.format("%04d%02d", hoje.getYear(), hoje.getMonthValue());

        int next = 1;
        var ultimoOpt = hospedagemRepository
                .findTopByCodigoHospedagemStartingWithOrderByCodigoHospedagemDesc(prefix);

        if (ultimoOpt.isPresent()) {
            String ult = ultimoOpt.get().getCodigoHospedagem();
            String sufix = ult.substring(prefix.length());
            try { next = Integer.parseInt(sufix) + 1; } catch (NumberFormatException ignore) {}
        }

        return prefix + String.format("%03d", next);
    }

    private boolean existeConflito(Quarto quarto, LocalDate inicioInclusivo, LocalDate fimExclusivo) {
        LocalDate ultimoDia = fimExclusivo.minusDays(1);
        return hospedagemRepository
                .existsByQuartoAndDataEntradaLessThanEqualAndDataSaidaGreaterThan(
                        quarto, ultimoDia, inicioInclusivo
                );
    }

    private boolean existeConflitoEditando(Hospedagem atual, LocalDate inicioInclusivo, LocalDate fimExclusivo) {
        LocalDate ultimoDia = fimExclusivo.minusDays(1);
        return hospedagemRepository.findAllComQuarto().stream()
                .filter(h -> !Objects.equals(h.getId(), atual.getId()))
                .filter(h -> h.getQuarto() != null &&
                        atual.getQuarto() != null &&
                        Objects.equals(h.getQuarto().getId(), atual.getQuarto().getId()))
                .anyMatch(h ->
                        (h.getDataEntrada() == null || !h.getDataEntrada().isAfter(ultimoDia)) &&
                                (h.getDataSaida() == null || h.getDataSaida().isAfter(inicioInclusivo))
                );
    }

    /** helper pra saber se a hospedagem é da PREFEITURA */
    private boolean isPrefeitura(Hospedagem h) {
        return h != null && h.getTipo() == TipoHospedagem.PREFEITURA;
    }

    /**
     * NOVA LÓGICA: "hospedagem ativa" para agendamentos
     * - Não cancelada
     * - Data de saída = hoje
     * - Quarto ainda está OCUPADO (não fez checkout manual)
     */
    private boolean isAtivaParaCheckoutAutomatico(Hospedagem h) {
        if (h == null) return false;

        // Cancelada nunca é ativa
        if (Boolean.TRUE.equals(h.getCancelada()) || Boolean.TRUE.equals(h.getCancelado())) {
            return false;
        }

        LocalDate hoje = todayBr();
        LocalDate dataSaida = h.getDataSaida();

        // Só considera ativa se a data de saída é HOJE
        if (dataSaida == null || !dataSaida.isEqual(hoje)) {
            return false;
        }

        // E o quarto ainda está OCUPADO (não fez checkout manual)
        StatusQuarto statusQuarto = (h.getQuarto() != null ? h.getQuarto().getStatus() : null);
        return statusQuarto == StatusQuarto.OCUPADO;
    }

    /**
     * Lógica para grade/listagem - mantém a original
     */
    private boolean isAtiva(Hospedagem h) {
        if (h == null) return false;

        if (Boolean.TRUE.equals(h.getCancelada()) || Boolean.TRUE.equals(h.getCancelado())) {
            return false;
        }

        LocalDate hoje = todayBr();
        LocalDate dataSaida = h.getDataSaida();
        StatusQuarto statusQuarto = (h.getQuarto() != null ? h.getQuarto().getStatus() : null);

        // se quarto está OCUPADO
        if (statusQuarto == StatusQuarto.OCUPADO) {
            if (dataSaida == null) return true;
            if (dataSaida.isAfter(hoje)) return true;
            if (dataSaida.isEqual(hoje)) return true;
            return true;
        }

        // quarto não está mais ocupado
        if (dataSaida == null) {
            return false;
        }

        if (dataSaida.isAfter(hoje)) {
            return true;
        }

        return false;
    }

    /* ============================ CHECK-IN ============================ */
    @Transactional
    public Hospedagem realizarCheckin(HospedagemDTO dto) {
        LocalDate hoje = todayBr();

        if (dto.getNumeroQuarto() == null || dto.getNumeroQuarto().isBlank())
            throw new CampoObrigatorioException("Número do quarto é obrigatório.");
        if (dto.getNumeroDiarias() == null || dto.getNumeroDiarias() <= 0)
            throw new CampoObrigatorioException("Número de diárias deve ser maior que zero.");
        if (dto.getValorDiaria() == null || dto.getValorDiaria() <= 0)
            throw new ValorInvalidoException("Valor da diária inválido.");
        if (dto.getNome() == null || dto.getNome().isBlank())
            throw new CampoObrigatorioException("Nome é obrigatório.");
        if (dto.getFormaPagamento() == null || dto.getFormaPagamento().isBlank())
            throw new CampoObrigatorioException("Forma de pagamento é obrigatória.");

        Quarto quarto = quartoRepository.findByNumero(dto.getNumeroQuarto())
                .orElseThrow(() -> new QuartoNaoEncontradoException(dto.getNumeroQuarto()));

        // não pode ter reserva ativa hoje
        boolean temReservaHoje = !reservaRepository
                .findConflitos(quarto,
                        java.util.List.of(StatusReserva.PENDENTE, StatusReserva.CONFIRMADA),
                        hoje, hoje.plusDays(1))
                .isEmpty();
        if (temReservaHoje)
            throw new QuartoOcupadoException("O quarto " + quarto.getNumero() + " possui uma reserva ativa hoje.");

        if (quarto.getStatus() == StatusQuarto.OCUPADO)
            throw new QuartoOcupadoException("O quarto " + quarto.getNumero() + " já está ocupado.");
        if (quarto.getStatus() == StatusQuarto.MANUTENCAO)
            throw new QuartoOcupadoException("O quarto " + quarto.getNumero() + " está em manutenção.");

        LocalDate dataEntrada = hoje;
        LocalDate dataSaida = hoje.plusDays(dto.getNumeroDiarias());

        if (existeConflito(quarto, dataEntrada, dataSaida))
            throw new QuartoOcupadoException("Já existe hospedagem ativa nesse período para o quarto " + quarto.getNumero());

        Hospedagem h = new Hospedagem();
        h.setNome(dto.getNome());
        h.setCpf(dto.getCpf());

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            h.setEmail(dto.getEmail().trim().toLowerCase());
        }

        h.setDataEntrada(dataEntrada);
        h.setDataSaida(dataSaida);
        h.setValorDiaria(dto.getValorDiaria());
        h.setValorTotal(dto.getValorDiaria() * dto.getNumeroDiarias());
        h.setFormaPagamento(dto.getFormaPagamento());
        h.setObservacoes(dto.getObservacoes());
        h.setTipo(dto.getTipo() != null ? dto.getTipo() : TipoHospedagem.COMUM);
        h.setCodigoHospedagem(gerarCodigoSequencialMensal());
        h.setQuarto(quarto);

        h.setCriadoPor(usuarioAtualOuSystem());
        h.setCriadoPorId(usuarioAtualIdOrNull());
        h.setCriadoPorNome(usuarioAtualNomeOrNull());
        h.setCriadoEm(nowBr());

        quarto.setStatus(StatusQuarto.OCUPADO);
        quartoRepository.save(quarto);
        hospedagemRepository.save(h);

        // >>> financeiro: marca se é prefeitura
        boolean prefeitura = isPrefeitura(h);
        try {
            financeiro.registrarEntradaHospedagem(
                    h.getId(),
                    h.getValorTotal(),
                    h.getFormaPagamento(),
                    h.getCodigoHospedagem(),
                    prefeitura
            );
        } catch (Exception e) {
            System.err.println("[FIN] Falha ao registrar entrada da hospedagem: " + e.getMessage());
        }

        safeNotify(() -> notifier.hospedagemCriada(h), "hospedagemCriada");
        return h;
    }

    /* ============================ BUSCAR POR ID ============================ */
    @Transactional(Transactional.TxType.SUPPORTS)
    public HospedagemResponseDTO buscarPorIdDTO(Long id) {
        Hospedagem h = hospedagemRepository.findByIdComQuarto(id)
                .orElseThrow(() -> new HospedagemNaoEncontradaException("Hospedagem não encontrada."));
        return toResponseDTO(h);
    }

    /* ============================ EDITAR ============================ */
    @Transactional
    public Hospedagem editarHospedagem(Long id, HospedagemDTO dto) {
        Hospedagem h = hospedagemRepository.findByIdComQuarto(id)
                .orElseThrow(() -> new HospedagemNaoEncontradaException("Hospedagem não encontrada."));

        if (h.getDataSaida() != null && !h.getDataSaida().isAfter(todayBr()))
            throw new OperacaoNaoPermitidaException("Não é possível editar uma hospedagem já finalizada.");

        StringBuilder resumo = new StringBuilder();
        String formaPagamentoAntiga = h.getFormaPagamento();
        Double valorAnterior = h.getValorTotal();

        if (dto.getNumeroDiarias() != null && dto.getNumeroDiarias() > 0) {
            LocalDate dataEntrada = h.getDataEntrada() != null ? h.getDataEntrada() : todayBr();
            LocalDate novaSaida = dataEntrada.plusDays(dto.getNumeroDiarias());

            if (h.getQuarto() != null && existeConflitoEditando(h, dataEntrada, novaSaida)) {
                throw new QuartoOcupadoException(
                        "Já existe hospedagem nesse período para o quarto " + h.getQuarto().getNumero() + "."
                );
            }

            if (h.getQuarto() != null) {
                boolean temReservaConflito = !reservaRepository
                        .findConflitos(h.getQuarto(),
                                java.util.List.of(StatusReserva.PENDENTE, StatusReserva.CONFIRMADA),
                                dataEntrada, novaSaida)
                        .isEmpty();
                if (temReservaConflito) {
                    throw new QuartoOcupadoException(
                            "Existe reserva ativa nesse período para o quarto " + h.getQuarto().getNumero() + "."
                    );
                }
            }

            h.setDataSaida(novaSaida);
            Double vd = (dto.getValorDiaria() != null ? dto.getValorDiaria() : h.getValorDiaria());
            if (vd == null) vd = 0d;
            h.setValorDiaria(vd);
            h.setValorTotal(vd * dto.getNumeroDiarias());
            resumo.append("Diárias: ").append(dto.getNumeroDiarias()).append(". ");
        }

        if (dto.getValorDiaria() != null) {
            h.setValorDiaria(dto.getValorDiaria());
            Integer diarias = (h.getDataEntrada() != null && h.getDataSaida() != null)
                    ? (int) ChronoUnit.DAYS.between(h.getDataEntrada(), h.getDataSaida()) : 1;
            h.setValorTotal(dto.getValorDiaria() * Math.max(diarias, 1));
            resumo.append("Valor diária: R$ ").append(dto.getValorDiaria()).append(". ");
        }

        if (dto.getFormaPagamento() != null) {
            h.setFormaPagamento(dto.getFormaPagamento());
            resumo.append("pagamento: ").append(formaPagamentoAntiga != null ? formaPagamentoAntiga : "-")
                    .append(" -> ")
                    .append(dto.getFormaPagamento())
                    .append(". ");
        }

        if (dto.getObservacoes() != null) {
            h.setObservacoes(dto.getObservacoes());
            resumo.append("Obs atualizadas. ");
        }

        if (dto.getTipo() != null) {
            h.setTipo(dto.getTipo());
            resumo.append("Tipo: ").append(dto.getTipo()).append(". ");
        }

        if (dto.getEmail() != null) {
            if (dto.getEmail().isBlank()) {
                h.setEmail(null);
            } else {
                h.setEmail(dto.getEmail().trim().toLowerCase());
            }
            resumo.append("E-mail atualizado. ");
        }

        if (dto.getNumeroQuarto() != null) {
            String atualNum = (h.getQuarto() != null ? h.getQuarto().getNumero() : null);
            if (!dto.getNumeroQuarto().equals(atualNum)) {
                Quarto novo = quartoRepository.findByNumero(dto.getNumeroQuarto())
                        .orElseThrow(() -> new QuartoNaoEncontradoException(dto.getNumeroQuarto()));

                if (novo.getStatus() == StatusQuarto.MANUTENCAO)
                    throw new QuartoOcupadoException("O quarto " + novo.getNumero() + " está em manutenção.");

                LocalDate ini = h.getDataEntrada();
                LocalDate fim = (h.getDataSaida() != null) ? h.getDataSaida() : ini.plusDays(1);

                boolean conflito = hospedagemRepository.findAllComQuarto().stream()
                        .filter(x -> !Objects.equals(x.getId(), h.getId()))
                        .filter(x -> x.getQuarto() != null && Objects.equals(x.getQuarto().getId(), novo.getId()))
                        .anyMatch(x ->
                                (x.getDataEntrada() == null || !x.getDataEntrada().isAfter(fim.minusDays(1))) &&
                                        (x.getDataSaida() == null || x.getDataSaida().isAfter(ini))
                        );
                if (conflito)
                    throw new QuartoOcupadoException("Já existe hospedagem nesse período para o quarto " + novo.getNumero() + ".");

                Quarto antigo = h.getQuarto();
                if (antigo != null && antigo.getStatus() == StatusQuarto.OCUPADO) {
                    antigo.setStatus(StatusQuarto.DISPONIVEL);
                    quartoRepository.save(antigo);
                }
                novo.setStatus(StatusQuarto.OCUPADO);
                quartoRepository.save(novo);

                h.setQuarto(novo);
                resumo.append("Quarto alterado para ").append(novo.getNumero()).append(". ");
            }
        }

        hospedagemRepository.save(h);
        try { resyncEntradaFinanceira(h, valorAnterior); } catch (Exception ignore) {}

        safeNotify(() -> notifier.hospedagemAtualizada(h, resumo.toString().trim()), "hospedagemAtualizada");
        return hospedagemRepository.findByIdComQuarto(h.getId()).orElse(h);
    }

    /* ============================ CHECK-OUT MANUAL ============================ */
    @Transactional
    public Hospedagem realizarCheckoutPorNumero(CheckoutDTO dto) {
        if (dto.getNumeroQuarto() == null || dto.getNumeroQuarto().isBlank())
            throw new CampoObrigatorioException("Número do quarto é obrigatório.");
        if (dto.getDescricao() == null || dto.getDescricao().isBlank())
            throw new CampoObrigatorioException("Descrição do motivo da saída é obrigatória.");

        Quarto quarto = quartoRepository.findByNumero(dto.getNumeroQuarto())
                .orElseThrow(() -> new QuartoNaoEncontradoException(dto.getNumeroQuarto()));

        if (quarto.getStatus() != StatusQuarto.OCUPADO)
            throw new QuartoJaLivreException("O quarto " + quarto.getNumero() + " já está livre.");

        Hospedagem h = hospedagemRepository.findTopByQuartoOrderByIdDesc(quarto)
                .orElseThrow(() -> new HospedagemNaoEncontradaException("Nenhuma hospedagem ativa encontrada."));

        LocalDate hoje = todayBr();
        long diasHospedados = ChronoUnit.DAYS.between(h.getDataEntrada(), hoje);
        if (diasHospedados <= 0) diasHospedados = 1;

        h.setDataSaida(hoje);
        Double vd = h.getValorDiaria() != null ? h.getValorDiaria() : 0d;
        Double valorAnterior = h.getValorTotal();
        h.setValorTotal(vd * diasHospedados);
        h.setObservacoes((h.getObservacoes() == null ? "" : h.getObservacoes() + " | ") + dto.getDescricao());

        quarto.setStatus(StatusQuarto.DISPONIVEL);

        hospedagemRepository.save(h);
        quartoRepository.save(quarto);

        try { resyncEntradaFinanceira(h, valorAnterior); } catch (Exception ignore) {}

        safeNotify(() -> notifier.hospedagemCheckoutManual(h, dto.getDescricao()), "checkoutManual");
        return h;
    }

    /* ============================ DELETE ============================ */
    @Transactional
    public void deletarHospedagem(Long id) {
        if (!hasAnyRole("ADMIN", "DEV"))
            throw new OperacaoNaoPermitidaException("Apenas ADMIN ou DEV podem excluir hospedagens.");

        Hospedagem h = hospedagemRepository.findById(id)
                .orElseThrow(() -> new HospedagemNaoEncontradaException("Hospedagem não encontrada."));

        try {
            financeiro.cancelarEntradaDeHospedagem(h.getId(), "Hospedagem excluída");
        } catch (Exception e) {
            System.err.println("[FIN] Falha ao cancelar entrada: " + e.getMessage());
        }

        Quarto q = h.getQuarto();
        if (q != null && q.getStatus() == StatusQuarto.OCUPADO) {
            q.setStatus(StatusQuarto.DISPONIVEL);
            quartoRepository.save(q);
        }

        String executadoPor = usuarioAtualOuSystem();
        hospedagemRepository.delete(h);

        safeNotify(() -> notifier.hospedagemAtualizada(h, "Excluída por: " + executadoPor), "hospedagemExcluida");
    }


    /* ============================ DTO Response ============================ */
    public HospedagemResponseDTO toResponseDTO(Hospedagem h) {
        String status = isAtiva(h) ? "Ativo" : "Inativo";

        Integer numeroDiarias = (h.getDataEntrada() != null && h.getDataSaida() != null)
                ? (int) ChronoUnit.DAYS.between(h.getDataEntrada(), h.getDataSaida())
                : null;

        Boolean ocupado = h.getQuarto() != null && h.getQuarto().getStatus() == StatusQuarto.OCUPADO;

        return new HospedagemResponseDTO(
                h.getId(), h.getTipo(), h.getNome(), h.getCpf(), h.getEmail(),
                h.getDataEntrada(), h.getDataSaida(), numeroDiarias, h.getValorDiaria(),
                h.getValorTotal(), h.getFormaPagamento(), h.getObservacoes(),
                h.getQuarto() != null ? h.getQuarto().getNumero() : null, ocupado, status,
                h.getCodigoHospedagem(), h.getCriadoPor(), h.getCriadoEm(),
                h.getCriadoPorNome(), h.getCriadoPorId()
        );
    }

    /* ============================ CRIAR A PARTIR DE RESERVA ============================ */
    @Transactional
    public Hospedagem criarAPartirDaReserva(Reserva reserva) {
        if (reserva == null)
            throw new IllegalArgumentException("Reserva não pode ser nula.");

        Quarto quarto = reserva.getQuarto();
        if (quarto == null)
            throw new IllegalArgumentException("Reserva não possui quarto associado.");

        if (quarto.getStatus() == StatusQuarto.OCUPADO)
            throw new QuartoOcupadoException("O quarto " + quarto.getNumero() + " já está ocupado.");

        Hospedagem h = new Hospedagem();
        h.setNome(reserva.getNome());
        h.setCpf(reserva.getCpf());

        h.setDataEntrada(reserva.getDataEntrada());
        h.setDataSaida(reserva.getDataSaida());
        h.setValorDiaria(reserva.getValorDiaria());
        h.setValorTotal(reserva.getValorTotal());
        h.setFormaPagamento(reserva.getFormaPagamento());
        h.setObservacoes(
                (reserva.getObservacoes() != null ? reserva.getObservacoes() : "") +
                        (reserva.getObservacoesCheckin() != null ? " | " + reserva.getObservacoesCheckin() : "")
        );
        h.setTipo(reserva.getTipoCliente() != null
                ? TipoHospedagem.valueOf(reserva.getTipoCliente().name())
                : TipoHospedagem.COMUM);
        h.setCodigoHospedagem("R-" + reserva.getCodigo());
        h.setQuarto(quarto);

        h.setCriadoPor("reserva:" + reserva.getCodigo());
        h.setCriadoPorId(null);
        h.setCriadoPorNome(null);
        h.setCriadoEm(nowBr());

        quarto.setStatus(StatusQuarto.OCUPADO);
        quartoRepository.save(quarto);
        hospedagemRepository.save(h);

        // >>> financeiro: marca se essa hospedagem é da prefeitura
        boolean prefeitura = isPrefeitura(h);
        try {
            financeiro.registrarEntradaHospedagem(
                    h.getId(),
                    h.getValorTotal(),
                    h.getFormaPagamento(),
                    h.getCodigoHospedagem(),
                    prefeitura
            );
        } catch (Exception e) {
            System.err.println("[FIN] Falha ao registrar entrada (via reserva): " + e.getMessage());
        }

        safeNotify(() -> notifier.hospedagemCriada(h), "hospedagemCriadaViaReserva");
        return h;
    }

    /* ============================ LISTAGEM ============================ */
    public List<Hospedagem> listar(String nome, TipoHospedagem tipo,
                                   LocalDate dataInicio, LocalDate dataFim, String status) {

        return hospedagemRepository.findAllComQuarto().stream()
                .filter(h -> !Boolean.TRUE.equals(h.getCancelada()) && !Boolean.TRUE.equals(h.getCancelado()))
                .filter(h -> {
                    if (nome == null || nome.isBlank()) return true;
                    String n = h.getNome();
                    return n != null && n.toLowerCase().contains(nome.toLowerCase());
                })
                .filter(h -> tipo == null || h.getTipo() == tipo)
                .filter(h -> {
                    if (dataInicio == null && dataFim == null) return true;
                    LocalDate entrada = h.getDataEntrada();
                    if (entrada == null) return false;
                    if (dataInicio != null && entrada.isBefore(dataInicio)) return false;
                    if (dataFim != null && entrada.isAfter(dataFim)) return false;
                    return true;
                })
                .filter(h -> {
                    if (status == null || status.isBlank() || status.equalsIgnoreCase("TODAS")) {
                        return true;
                    }
                    boolean ativa = isAtiva(h);
                    if (status.equalsIgnoreCase("ATIVAS") || status.equalsIgnoreCase("ATIVA")) {
                        return ativa;
                    }
                    if (status.equalsIgnoreCase("INATIVAS") || status.equalsIgnoreCase("INATIVA")) {
                        return !ativa;
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Hospedagem::getDataEntrada,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Hospedagem::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /* ============================ Financeiro Resync ============================ */
    private void resyncEntradaFinanceira(Hospedagem h, Double valorAnterior) {
        Double atual = h.getValorTotal();
        if (atual == null || atual <= 0) return;
        if (valorAnterior == null) valorAnterior = 0d;

        double diff = Math.abs(atual - valorAnterior);
        if (diff < 0.01) return;

        try {
            financeiro.cancelarEntradaDeHospedagem(h.getId(), "Ajuste de valor da hospedagem");

            boolean prefeitura = isPrefeitura(h);

            financeiro.registrarEntradaHospedagem(
                    h.getId(),
                    atual,
                    h.getFormaPagamento(),
                    h.getCodigoHospedagem(),
                    prefeitura
            );
        } catch (Exception e) {
            System.err.println("[FIN] Falha no resync da entrada: " + e.getMessage());
        }
    }
}
