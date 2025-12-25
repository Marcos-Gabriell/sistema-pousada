package br.com.pousda.pousada.quartos.application;

import br.com.pousda.pousada.notificacoes.application.facade.NotifierFacade;
import br.com.pousda.pousada.quartos.domain.Quarto;
import br.com.pousda.pousada.quartos.domain.enuns.StatusQuarto;
import br.com.pousda.pousada.quartos.domain.enuns.TipoQuarto;
import br.com.pousda.pousada.quartos.dtos.QuartoChangeSet;
import br.com.pousda.pousada.quartos.infra.QuartoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class QuartoService {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private QuartoRepository quartoRepository;

    @Autowired(required = false)
    private NotifierFacade notifier;

    // ================== CONSULTAS ==================

    public List<Quarto> listarTodos() { return quartoRepository.findAll(); }

    public Optional<Quarto> buscarPorId(Long id) { return quartoRepository.findById(id); }

    public List<Quarto> listarPorStatus(StatusQuarto status) { return quartoRepository.findByStatus(status); }

    public List<Quarto> listarPorTipo(TipoQuarto tipo) { return quartoRepository.findByTipo(tipo); }

    public List<Quarto> buscarPorNumeroOuNome(String termo) {
        String t = termo == null ? "" : termo.trim();
        return t.isEmpty() ? quartoRepository.findAll() : quartoRepository.searchByNumeroOrNome(t);
    }

    public List<Quarto> filtrar(StatusQuarto status, TipoQuarto tipo, String termo) {
        String t = termo == null ? "" : termo.trim();
        boolean hasTerm = !t.isEmpty();

        if (status != null && tipo != null && hasTerm) {
            return quartoRepository.searchByStatusAndTipoAndTerm(status, tipo, t);
        } else if (status != null && tipo != null) {
            return quartoRepository.findByStatusAndTipo(status, tipo);
        } else if (status != null && hasTerm) {
            return quartoRepository.searchByStatusAndTerm(status, t);
        } else if (tipo != null && hasTerm) {
            return quartoRepository.searchByTipoAndTerm(tipo, t);
        } else if (status != null) {
            return quartoRepository.findByStatus(status);
        } else if (tipo != null) {
            return quartoRepository.findByTipo(tipo);
        } else if (hasTerm) {
            return quartoRepository.searchByNumeroOrNome(t);
        } else {
            return quartoRepository.findAll();
        }
    }

    public long contarTodos() { return quartoRepository.count(); }

    public long contarPorStatus(StatusQuarto status) { return quartoRepository.countByStatus(status); }

    // ================== COMANDOS ==================

    @Transactional
    public Quarto criar(Quarto quarto) {
        validarQuarto(quarto);

        if (quartoRepository.existsByNumero(quarto.getNumero())) {
            throw new IllegalArgumentException("Já existe um quarto com este número");
        }

        quarto.setStatus(StatusQuarto.DISPONIVEL);
        quarto.setDataManutencaoDesde(null);

        Quarto salvo = quartoRepository.save(quarto);

        dispararNotificacao(() -> { if (notifier != null) notifier.quartoCriado(salvo); });

        return salvo;
    }

    @Transactional
    public Quarto editar(Long id, Quarto atualizado) {
        validarQuarto(atualizado);

        Quarto existente = quartoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Quarto não encontrado"));

        var changeSet = QuartoChangeSet.diff(
                existente.getNumero(), atualizado.getNumero(),
                existente.getNome(),   atualizado.getNome(),
                existente.getTipo(),   atualizado.getTipo(),
                existente.getValorDiaria(), atualizado.getValorDiaria(),
                existente.getCapacidade(),  atualizado.getCapacidade(),
                existente.getDescricao(),   atualizado.getDescricao(),
                existente.getStatus(),      atualizado.getStatus()
        );

        if (!existente.getNumero().equals(atualizado.getNumero())
                && quartoRepository.existsByNumero(atualizado.getNumero())) {
            throw new IllegalArgumentException("Já existe outro quarto com este número");
        }
        if (atualizado.getStatus() == StatusQuarto.OCUPADO) {
            throw new IllegalStateException("Status OCUPADO é definido automaticamente pelo sistema de hospedagem.");
        }
        if (existente.estaOcupado()) {
            throw new IllegalStateException("Não é possível editar um quarto ocupado.");
        }
        if (atualizado.estaEmManutencao() && !existente.estaDisponivel()) {
            throw new IllegalStateException("Só é possível colocar em manutenção um quarto disponível.");
        }

        // aplicar mudanças
        StatusQuarto statusAntigo = existente.getStatus();
        StatusQuarto statusNovo   = atualizado.getStatus();

        existente.setNumero(atualizado.getNumero());
        existente.setNome(atualizado.getNome());
        existente.setTipo(atualizado.getTipo());
        existente.setValorDiaria(atualizado.getValorDiaria());
        existente.setDescricao(atualizado.getDescricao());
        existente.setCapacidade(atualizado.getCapacidade());

        if (statusNovo != statusAntigo) {
            if (statusNovo == StatusQuarto.MANUTENCAO) {
                existente.entrarEmManutencao();
            } else if (statusAntigo == StatusQuarto.MANUTENCAO && statusNovo == StatusQuarto.DISPONIVEL) {
                existente.liberarManutencao();
            } else {
                existente.setStatus(statusNovo);
                existente.setUltimaAlteracaoStatus(java.time.LocalDateTime.now(SP));
            }
        }

        Quarto salvo = quartoRepository.save(existente);

        dispararNotificacao(() -> {
            if (notifier != null) {
                notifier.quartoAtualizado(salvo, changeSet);
                if (statusNovo != statusAntigo) {
                    if (statusNovo == StatusQuarto.MANUTENCAO) notifier.quartoEntrouManutencao(salvo);
                    else if (statusAntigo == StatusQuarto.MANUTENCAO && statusNovo == StatusQuarto.DISPONIVEL)
                        notifier.quartoVoltouDisponivel(salvo);
                }
            }
        });

        return salvo;
    }

    @Transactional
    public Quarto colocarEmManutencao(Long id) {
        Quarto quarto = quartoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Quarto não encontrado"));

        if (quarto.estaOcupado()) {
            throw new IllegalStateException("Não é possível colocar em manutenção um quarto ocupado.");
        }
        if (!quarto.estaDisponivel()) {
            throw new IllegalStateException("Só é possível colocar em manutenção um quarto disponível.");
        }

        quarto.entrarEmManutencao();
        Quarto salvo = quartoRepository.save(quarto);

        dispararNotificacao(() -> {
            if (notifier != null) {
                notifier.quartoEntrouManutencao(salvo);
                // também registra atualização geral
                notifier.quartoAtualizado(salvo, QuartoChangeSet.diff(
                        null,null,null,null,null,null,null,null,null,null,null,null,
                        StatusQuarto.DISPONIVEL, StatusQuarto.MANUTENCAO
                ));
            }
        });

        return salvo;
    }

    @Transactional
    public Quarto liberarManutencao(Long id) {
        Quarto quarto = quartoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Quarto não encontrado"));

        if (!quarto.estaEmManutencao()) {
            throw new IllegalStateException("Só é possível liberar um quarto que está em manutenção.");
        }

        quarto.liberarManutencao();
        Quarto salvo = quartoRepository.save(quarto);

        dispararNotificacao(() -> {
            if (notifier != null) {
                notifier.quartoVoltouDisponivel(salvo);
                notifier.quartoAtualizado(salvo, QuartoChangeSet.diff(
                        null,null,null,null,null,null,null,null,null,null,null,null,
                        StatusQuarto.MANUTENCAO, StatusQuarto.DISPONIVEL
                ));
            }
        });

        return salvo;
    }

    @Transactional
    public void excluir(Long id) {
        Quarto quarto = quartoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Quarto não encontrado"));

        if (quarto.estaOcupado()) {
            throw new IllegalStateException("Não é possível excluir um quarto ocupado.");
        }

        dispararNotificacao(() -> { if (notifier != null) notifier.quartoExcluido(quarto); });
        quartoRepository.delete(quarto);
    }

    @Transactional
    public void ocuparQuarto(Long id) {
        Quarto quarto = quartoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Quarto não encontrado"));

        if (!quarto.estaDisponivel()) {
            throw new IllegalStateException("Só é possível ocupar um quarto disponível.");
        }

        quarto.ocupar();
        quartoRepository.save(quarto);
    }

    @Transactional
    public void desocuparQuarto(Long id) {
        Quarto quarto = quartoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Quarto não encontrado"));

        if (!quarto.estaOcupado()) {
            throw new IllegalStateException("Só é possível desocupar um quarto ocupado.");
        }

        quarto.desocupar();
        Quarto salvo = quartoRepository.save(quarto);

        dispararNotificacao(() -> { if (notifier != null) notifier.quartoVoltouDisponivel(salvo); });
    }


    private void validarQuarto(Quarto quarto) {
        if (quarto.getNumero() == null || quarto.getNumero().trim().isEmpty())
            throw new IllegalArgumentException("Número do quarto é obrigatório");
        if (quarto.getTipo() == null)
            throw new IllegalArgumentException("Tipo do quarto é obrigatório");
        if (quarto.getValorDiaria() == null || quarto.getValorDiaria().doubleValue() <= 0)
            throw new IllegalArgumentException("Valor da diária deve ser maior que zero");
    }

    private void dispararNotificacao(Runnable acaoNotificacao) {
        if (notifier != null) {
            try { acaoNotificacao.run(); }
            catch (Exception e) { log.error("Erro ao disparar notificação: {}", e.getMessage(), e); }
        }
    }
}
