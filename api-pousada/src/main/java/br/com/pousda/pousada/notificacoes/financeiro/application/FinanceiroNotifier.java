package br.com.pousda.pousada.notificacoes.financeiro.application;

import java.util.Set;

public interface FinanceiroNotifier {

    void lancamentoCriado(Long id, String tipo, String origem, Double valor, String descricao,
                          Long autorId, String autorJson, Set<Long> destinatarios);

    void lancamentoAtualizado(Long id, String tipo, String origem,
                              String deDescricao, Double deValor,
                              String paraDescricao, Double paraValor,
                              Long autorId, String autorJson, Set<Long> destinatarios);

    void lancamentoCancelado(Long id, String tipo, String origem, Double valor,
                             String motivo, String canceladoPorPerfil,
                             Long autorId, String autorJson, Set<Long> destinatarios);
}
