package br.com.pousda.pousada.notificacoes.quarto.application;

import br.com.pousda.pousada.quartos.domain.Quarto;

import java.util.Set;

public interface QuartoNotifier {
    void criado(Quarto quarto, Long autorId, String autorJson, Set<Long> destinatarios);
    void atualizado(Quarto quarto, String resumo, Long autorId, String autorJson, Set<Long> destinatarios);
    void entrouManutencao(Quarto quarto, Long autorId, String autorJson, Set<Long> destinatarios);
    void voltouDisponivel(Quarto quarto, Long autorId, String autorJson, Set<Long> destinatarios);
    void excluido(Quarto quarto, Long autorId, String autorJson, Set<Long> destinatarios);
}
