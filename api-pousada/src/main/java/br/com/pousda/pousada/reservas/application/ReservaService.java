package br.com.pousda.pousada.reservas.application;

import br.com.pousda.pousada.exception.QuartoOcupadoException;
import br.com.pousda.pousada.exception.ReservaNaoEncontradaException;
import br.com.pousda.pousada.exception.ValidacaoException;
import br.com.pousda.pousada.hospedagens.application.HospedagemService;
import br.com.pousda.pousada.notificacoes.application.facade.NotifierFacade;
import br.com.pousda.pousada.quartos.domain.Quarto;
import br.com.pousda.pousada.quartos.domain.enuns.StatusQuarto;
import br.com.pousda.pousada.quartos.infra.QuartoRepository;
import br.com.pousda.pousada.reservas.domain.Reserva;
import br.com.pousda.pousada.reservas.domain.StatusReserva;
import br.com.pousda.pousada.reservas.dtos.ConfirmarReservaDTO;
import br.com.pousda.pousada.reservas.dtos.ReservaDTO;
import br.com.pousda.pousada.reservas.dtos.ReservaResponseDTO;
import br.com.pousda.pousada.reservas.infra.ReservaRepository;
import br.com.pousda.pousada.security.SecurityUtils;
import br.com.pousda.pousada.usuarios.domain.Usuario;
import br.com.pousda.pousada.usuarios.infra.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ReservaService {

    private static final ZoneId ZONE_BR = ZoneId.of("America/Bahia");

    @Autowired private ReservaRepository reservaRepository;
    @Autowired private QuartoRepository quartoRepository;
    @Autowired private HospedagemService hospedagemService;
    @Autowired private NotifierFacade notifier;
    @Autowired private UsuarioRepository usuarioRepository;

    /* ==================== helpers de usuário ==================== */

    private Usuario getUsuarioLogado() {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            return usuarioRepository.findById(userId).orElse(null);
        } catch (Exception e) {
            log.warn("Não foi possível obter usuário logado: {}", e.getMessage());
            return null;
        }
    }

    private String gerarCodigoReservaMensal() {
        LocalDate hoje = LocalDate.now(ZONE_BR);
        YearMonth ym = YearMonth.from(hoje);
        final String prefix = String.format("%04d%02d", ym.getYear(), ym.getMonthValue()); // Ex: 202511

        // Busca a última reserva criada no mês corrente
        return reservaRepository.findTopByCodigoStartingWithOrderByCodigoDesc(prefix)
                .map(ultima -> {
                    String sufix = ultima.getCodigo().substring(prefix.length());
                    int next = Integer.parseInt(sufix) + 1;
                    return prefix + String.format("%03d", next);
                })
                .orElse(prefix + "001");
    }

    /* ==================== validação de criação/edição ==================== */

    private void validarDTO(ReservaDTO dto) {
        if (dto.getNome() == null || dto.getNome().isBlank())
            throw new ValidacaoException("Nome é obrigatório.");

        if (dto.getTipoCliente() == null)
            throw new ValidacaoException("Tipo de cliente é obrigatório.");

        if (dto.getNumeroQuarto() == null || dto.getNumeroQuarto().isBlank())
            throw new ValidacaoException("Número do quarto é obrigatório.");

        if (dto.getDataEntrada() == null)
            throw new ValidacaoException("Data de entrada é obrigatória.");

        if (dto.getNumeroDiarias() == null || dto.getNumeroDiarias() <= 0)
            throw new ValidacaoException("Número de diárias deve ser maior que zero.");

        if (dto.getValorDiaria() == null || dto.getValorDiaria() <= 0)
            throw new ValidacaoException("Valor da diária é obrigatório e deve ser maior que zero.");

        if (dto.getFormaPagamento() == null || dto.getFormaPagamento().isBlank())
            throw new ValidacaoException("Forma de pagamento é obrigatória.");

        if (dto.getTelefone() != null && !dto.getTelefone().isBlank()) {
            validarTelefone(dto.getTelefone());
        }

        if (dto.getCpf() != null && !dto.getCpf().isBlank()) {
            validarCPF(dto.getCpf());
        }

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            validarEmail(dto.getEmail());
        }

        if (dto.getObservacoes() == null) {
            dto.setObservacoes("");
        }

        validarDataEntrada(dto.getDataEntrada());
    }

    /**
     * Regra nova: reserva só pode ser feita para data FUTURA (a partir de amanhã).
     */
    private void validarDataEntrada(LocalDate dataEntrada) {
        LocalDate hoje = LocalDate.now(ZONE_BR);

        // não pode ontem, nem hoje – só depois de hoje
        if (!dataEntrada.isAfter(hoje)) {
            throw new ValidacaoException("A reserva só pode ser feita para datas futuras (a partir de amanhã).");
        }
    }

    private void validarCPF(String cpf) {
        String cpfLimpo = cpf.replaceAll("[^0-9]", "");
        if (cpfLimpo.length() != 11) {
            throw new ValidacaoException("CPF deve conter 11 dígitos.");
        }
        if (!isCPFValido(cpfLimpo)) {
            throw new ValidacaoException("CPF inválido.");
        }
    }

    private boolean isCPFValido(String cpf) {
        cpf = cpf.replaceAll("[^0-9]", "");
        if (cpf.length() != 11) return false;
        if (cpf.matches("(\\d)\\1{10}")) return false;

        int soma = 0;
        for (int i = 0; i < 9; i++) soma += (cpf.charAt(i) - '0') * (10 - i);
        int resto = soma % 11;
        int digito1 = (resto < 2) ? 0 : 11 - resto;

        soma = 0;
        for (int i = 0; i < 10; i++) soma += (cpf.charAt(i) - '0') * (11 - i);
        resto = soma % 11;
        int digito2 = (resto < 2) ? 0 : 11 - resto;

        return (cpf.charAt(9) - '0' == digito1) && (cpf.charAt(10) - '0' == digito2);
    }

    private void validarTelefone(String telefone) {
        String telefoneLimpo = telefone.replaceAll("[^0-9]", "");
        if (telefoneLimpo.length() < 10 || telefoneLimpo.length() > 11) {
            throw new ValidacaoException("Telefone deve conter 10 ou 11 dígitos.");
        }
    }

    private void validarEmail(String email) {
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ValidacaoException("Email inválido.");
        }
    }

    // ✅ VALIDAÇÃO MELHORADA DE CONFLITOS
    private void validarConflitoDeReservasMelhorado(Quarto quarto, LocalDate entrada, LocalDate saida, Long ignorarId) {
        List<Reserva> conflitos = reservaRepository
                .findConflitos(quarto, List.of(StatusReserva.PENDENTE, StatusReserva.CONFIRMADA), entrada, saida);

        boolean existeConflito = conflitos.stream()
                .anyMatch(r -> ignorarId == null || !r.getId().equals(ignorarId));

        if (existeConflito) {
            // Detalhar o conflito para melhor mensagem
            List<String> codigosConflito = conflitos.stream()
                    .filter(r -> ignorarId == null || !r.getId().equals(ignorarId))
                    .map(Reserva::getCodigo)
                    .collect(Collectors.toList());

            throw new ValidacaoException(
                    String.format("Conflito de reserva no quarto %s no período %s a %s. Reservas conflitantes: %s",
                            quarto.getNumero(), entrada, saida, String.join(", ", codigosConflito))
            );
        }
    }

    private LocalDate calcularDataSaida(LocalDate dataEntrada, Integer numeroDiarias) {
        return dataEntrada.plusDays(numeroDiarias);
    }

    /* ==================== toResponse ==================== */

    private String getNomeUsuario(Long usuarioId) {
        if (usuarioId == null) return null;
        return usuarioRepository.findById(usuarioId)
                .map(Usuario::getNome)
                .orElse(null);
    }

    private ReservaResponseDTO toResponseDTO(Reserva r) {
        Long autorId = r.getCreatedBy();
        String autorNome = getNomeUsuario(autorId);

        Long confirmadorId = r.getConfirmedBy();
        String confirmadorNome = getNomeUsuario(confirmadorId);

        Long canceladorId = r.getCancelledBy();
        String canceladorNome = getNomeUsuario(canceladorId);

        return new ReservaResponseDTO(
                r.getId(),
                r.getCodigo(),
                r.getNome(),
                r.getTelefone(),
                r.getCpf(),
                r.getEmail(),
                r.getTipoCliente(),
                r.getQuarto().getNumero(),
                r.getDataEntrada(),
                r.getDataSaida(),
                r.getNumeroDiarias(),
                r.getStatus().toString(),
                r.getObservacoes(),
                r.getObservacoesCheckin(),
                r.getDataReserva(),
                r.getValorDiaria(),
                r.getValorTotal(),
                r.getFormaPagamento(),
                r.getMotivoCancelamento(),
                autorId,
                autorNome,
                confirmadorId,
                confirmadorNome,
                r.getConfirmedEm(),
                canceladorId,
                canceladorNome,
                r.getCancelledEm()
        );
    }

    /* ==================== CREATE ==================== */

    public ReservaResponseDTO criarReserva(ReservaDTO dto) {
        validarDTO(dto);
        Usuario autor = getUsuarioLogado();

        Quarto quarto = quartoRepository.findByNumero(dto.getNumeroQuarto())
                .orElseThrow(() -> new ValidacaoException("Quarto não encontrado: " + dto.getNumeroQuarto()));

        if (quarto.getStatus() == StatusQuarto.MANUTENCAO)
            throw new ValidacaoException("Quarto em manutenção.");

        LocalDate entrada = dto.getDataEntrada();
        LocalDate saida = calcularDataSaida(entrada, dto.getNumeroDiarias());

        // ✅ USAR VALIDAÇÃO MELHORADA
        validarConflitoDeReservasMelhorado(quarto, entrada, saida, null);

        Reserva r = new Reserva();
        r.setNome(dto.getNome());
        r.setTelefone(dto.getTelefone() != null ? dto.getTelefone() : "");
        r.setCpf(dto.getCpf() != null ? dto.getCpf() : "");
        r.setEmail(dto.getEmail() != null ? dto.getEmail() : "");
        r.setTipoCliente(dto.getTipoCliente());
        r.setQuarto(quarto);
        r.setDataEntrada(entrada);
        r.setNumeroDiarias(dto.getNumeroDiarias());
        r.setDataSaida(saida);
        r.setObservacoes(dto.getObservacoes() != null ? dto.getObservacoes() : "");
        r.setStatus(StatusReserva.PENDENTE);
        r.setDataReserva(LocalDate.now(ZONE_BR));
        r.setCodigo(gerarCodigoReservaMensal());
        r.setValorDiaria(dto.getValorDiaria());
        r.setFormaPagamento(dto.getFormaPagamento());
        r.setValorTotal(dto.getValorDiaria() * dto.getNumeroDiarias());

        if (autor != null) {
            r.setCreatedBy(autor.getId());
        }

        reservaRepository.save(r);
        notifier.reservaCriada(r, autor);

        return toResponseDTO(r);
    }

    /* ==================== EDIT ==================== */

    public ReservaResponseDTO editarReserva(Long id, ReservaDTO dto) {
        validarDTO(dto);
        Usuario autor = getUsuarioLogado();

        Reserva r = reservaRepository.findById(id)
                .orElseThrow(() -> new ReservaNaoEncontradaException("Reserva não encontrada."));

        if (r.getStatus() != StatusReserva.PENDENTE)
            throw new ValidacaoException("Só é possível editar reservas PENDENTES.");

        // Capturar o estado anterior para notificação
        Reserva antes = new Reserva();
        antes.setNome(r.getNome());
        antes.setTelefone(r.getTelefone());
        antes.setCpf(r.getCpf());
        antes.setEmail(r.getEmail());
        antes.setTipoCliente(r.getTipoCliente());
        antes.setQuarto(r.getQuarto());
        antes.setDataEntrada(r.getDataEntrada());
        antes.setNumeroDiarias(r.getNumeroDiarias());
        antes.setDataSaida(r.getDataSaida());
        antes.setObservacoes(r.getObservacoes());
        antes.setValorDiaria(r.getValorDiaria());
        antes.setFormaPagamento(r.getFormaPagamento());
        antes.setValorTotal(r.getValorTotal());

        Quarto novoQuarto = quartoRepository.findByNumero(dto.getNumeroQuarto())
                .orElseThrow(() -> new ValidacaoException("Novo quarto não encontrado."));

        LocalDate novaEntrada = dto.getDataEntrada();
        LocalDate novaSaida = calcularDataSaida(novaEntrada, dto.getNumeroDiarias());

        validarConflitoDeReservasMelhorado(novoQuarto, novaEntrada, novaSaida, id);

        r.setNome(dto.getNome());
        r.setTelefone(dto.getTelefone() != null ? dto.getTelefone() : "");
        r.setCpf(dto.getCpf() != null ? dto.getCpf() : "");
        r.setEmail(dto.getEmail() != null ? dto.getEmail() : "");
        r.setTipoCliente(dto.getTipoCliente());
        r.setQuarto(novoQuarto);
        r.setDataEntrada(novaEntrada);
        r.setNumeroDiarias(dto.getNumeroDiarias());
        r.setDataSaida(novaSaida);
        r.setObservacoes(dto.getObservacoes() != null ? dto.getObservacoes() : "");
        r.setValorDiaria(dto.getValorDiaria());
        r.setFormaPagamento(dto.getFormaPagamento());
        r.setValorTotal(dto.getValorDiaria() * dto.getNumeroDiarias());

        reservaRepository.save(r);
        notifier.reservaAtualizada(antes, r, autor);

        return toResponseDTO(r);
    }

    /* ==================== CANCELAR ==================== */

    public void cancelarReserva(Long id, String motivo) {
        Usuario autor = getUsuarioLogado();
        Reserva r = reservaRepository.findById(id)
                .orElseThrow(() -> new ReservaNaoEncontradaException("Reserva não encontrada."));

        r.setStatus(StatusReserva.CANCELADA);
        r.setCancelledBy(autor != null ? autor.getId() : null);
        r.setCancelledEm(LocalDateTime.now());
        r.setMotivoCancelamento(
                (motivo != null && !motivo.isBlank())
                        ? motivo
                        : "Reserva cancelada."
        );

        reservaRepository.save(r);
        notifier.reservaCancelada(r, autor, r.getMotivoCancelamento());
    }

    /* ==================== CONFIRMAR ==================== */

    public ReservaResponseDTO confirmarReserva(Long id, ConfirmarReservaDTO dto) {
        Usuario autor = getUsuarioLogado();
        Reserva r = reservaRepository.findById(id)
                .orElseThrow(() -> new ReservaNaoEncontradaException("Reserva não encontrada."));

        log.info("➡️ Iniciando confirmação da reserva id={} codigo={} quarto={} status={}",
                r.getId(),
                r.getCodigo(),
                r.getQuarto() != null ? r.getQuarto().getNumero() : "N/A",
                r.getStatus());

        if (r.getStatus() == StatusReserva.CANCELADA)
            throw new ValidacaoException("Não é possível confirmar uma reserva cancelada.");

        try {
            // aqui é o que você pediu: não obrigar CPF/email/telefone
            if (dto != null && dto.getTipoCliente() != null) {
                r.setTipoCliente(dto.getTipoCliente());
            }
            if (dto != null && dto.getObservacoesCheckin() != null && !dto.getObservacoesCheckin().isBlank()) {
                r.setObservacoesCheckin(dto.getObservacoesCheckin());
            }

            r.setStatus(StatusReserva.CONFIRMADA);
            r.setConfirmedBy(autor != null ? autor.getId() : null);
            r.setConfirmedEm(LocalDateTime.now());

            reservaRepository.save(r);

            log.info("Reserva {} marcada como CONFIRMADA. Criando hospedagem...", r.getCodigo());

            // cria hospedagem
            hospedagemService.criarAPartirDaReserva(r);

            notifier.reservaConfirmada(r, autor);

            log.info("✔ Reserva {} confirmada e hospedagem criada com sucesso.", r.getCodigo());
            return toResponseDTO(r);

        } catch (QuartoOcupadoException e) {
            log.error("❌ Erro ao confirmar reserva {}: quarto ocupado. Msg={}",
                    r.getCodigo(), e.getMessage(), e);
            // converte pra ValidacaoException pra voltar 400 amigável pro front
            throw new ValidacaoException(e.getMessage());
        } catch (ValidacaoException e) {
            log.error("❌ Erro de validação ao confirmar reserva {}: {}",
                    r.getCodigo(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("❌ Erro inesperado ao confirmar reserva {}: {}",
                    r.getCodigo(), e.getMessage(), e);
            throw new ValidacaoException("Falha ao confirmar reserva: " + e.getMessage());
        }
    }

    /* ==================== LISTAR ==================== */

    public List<ReservaResponseDTO> listarReservas() {
        return reservaRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public List<ReservaResponseDTO> listarReservasPorStatus(String status) {
        try {
            StatusReserva statusReserva = StatusReserva.valueOf(status.toUpperCase());
            return reservaRepository.findByStatus(statusReserva)
                    .stream()
                    .map(this::toResponseDTO)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new ValidacaoException("Status inválido: " + status);
        }
    }

    public Optional<ReservaResponseDTO> buscarPorId(Long id) {
        return reservaRepository.findById(id).map(this::toResponseDTO);
    }

    public List<Quarto> listarQuartosDisponiveis(LocalDate dataEntrada, LocalDate dataSaida) {
        if (dataEntrada == null || dataSaida == null)
            throw new ValidacaoException("Data de entrada e saída são obrigatórias.");

        LocalDate hoje = LocalDate.now(ZONE_BR);

        // mesma regra da reserva: só data futura
        if (!dataEntrada.isAfter(hoje))
            throw new ValidacaoException("Data de entrada deve ser uma data futura (a partir de amanhã).");

        if (dataSaida.isBefore(dataEntrada))
            throw new ValidacaoException("Data de saída não pode ser anterior à data de entrada.");

        List<Quarto> todos = quartoRepository.findAll();
        return todos.stream()
                .filter(q -> q.getStatus() != StatusQuarto.MANUTENCAO)
                .filter(q -> {
                    try {
                        validarConflitoDeReservasMelhorado(q, dataEntrada, dataSaida, null);
                        return true;
                    } catch (ValidacaoException e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }
}
