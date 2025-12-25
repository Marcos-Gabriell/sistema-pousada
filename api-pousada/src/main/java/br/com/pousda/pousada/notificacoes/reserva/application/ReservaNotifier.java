package br.com.pousda.pousada.notificacoes.reserva.application;

import java.util.Set;

public interface ReservaNotifier {

    void criada(Long reservaId,
                String codigo,
                String hospede,
                String quarto,
                String periodo,
                Long autorId,
                String autorJson,
                Set<Long> destinatarios);

    void atualizada(Long reservaId,
                    String codigo,
                    String campo,
                    String de,
                    String para,
                    Long autorId,
                    String autorJson,
                    Set<Long> destinatarios);

    void confirmada(Long reservaId,
                    String codigo,
                    String hospede,
                    Long autorId,
                    String autorJson,
                    Set<Long> destinatarios);

    void cancelada(Long reservaId,
                   String codigo,
                   String motivo,
                   Long autorId,
                   String autorJson,
                   Set<Long> destinatarios);

    void resumoVespera(int qtd, String listaQuartos, Set<Long> destinatarios);

    default void resumoVespera(int qtd, Set<Long> destinatarios) {
        resumoVespera(qtd, null, destinatarios);
    }

    void hojePendente(int qtd, String listaQuartos, Set<Long> destinatarios);

    default void hojePendente(int qtd, Set<Long> destinatarios) {
        hojePendente(qtd, null, destinatarios);
    }

    void ultimaChamada(Long reservaId,
                       String codigo,
                       String hospede,
                       String quarto,
                       Set<Long> destinatarios);

    void ultimaChamadaLote(int qtd, Set<Long> destinatarios);

    void naoConfirmadaCancelada(Long reservaId,
                                String codigo,
                                String hospede,
                                Set<Long> destinatarios);

    void naoConfirmadaCanceladaLote(int qtd, Set<Long> destinatarios);
}