// br/com/pousda/pousada/notificacoes/hospedagem/application/HospedagemNotifier.java
package br.com.pousda.pousada.notificacoes.hospedagem.application;

import java.util.Set;

public interface HospedagemNotifier {
    void criada(Long hospedagemId, String codigo, String autorNome,
                String hospede, String quarto, String checkin, String checkout,
                Long autorId, String autorJson, Set<Long> destinatarios);

    void atualizada(Long hospedagemId, String codigo, String autorNome,
                    String hospede, String campo, String de, String para,
                    Long autorId, String autorJson, Set<Long> destinatarios);



    // checkout manual (feito pelo usuário)
    void checkoutManual(Long hospedagemId, String codigo, String autorNome,
                        String hospede, String quarto, String dataSaida, String motivo,
                        Long autorId, String autorJson, Set<Long> destinatarios);
    // lembrete 11h (10:00)
    void checkoutLembrete11h(Long hospedagemId, String codigo,
                             String hospede, String quarto, String dataSaida,
                             Set<Long> destinatarios);

    // checkout feito automaticamente às 12:00
    void checkoutAutomaticoConcluido(Long hospedagemId, String codigo,
                                     String hospede, String quarto, String dataSaida,
                                     Set<Long> destinatarios);
}
