package br.com.pousda.pousada.notificacoes.hospedagem.application;

import br.com.pousda.pousada.notificacoes.application.NotificationService;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationOrigin;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import static br.com.pousda.pousada.notificacoes.hospedagem.application.HospedagemMsgFormatter.*;

@Service
@RequiredArgsConstructor
public class HospedagemNotifierImpl implements HospedagemNotifier {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private final NotificationService notifications;

    private String today() { return LocalDate.now(SP).toString(); }

    @Override
    public void criada(Long hospedagemId, String codigo, String autorNome,
                       String hospede, String quarto, String checkin, String checkout,
                       Long autorId, String autorJson, Set<Long> destinatarios) {

        String titulo   = tituloCriada();
        String mensagem = msgCriada(hospedagemId, codigo, autorNome, quarto, checkin, checkout);
        String body     = mensagem;

        notifications.send(
                NotificationType.HOSPEDAGEM_CRIADA,
                titulo,
                body,
                "/hospedagens/" + hospedagemId,
                "ABRIR_HOSPEDAGEM",
                hospedagemId,
                today(),
                autorId,
                autorJson,
                NotificationOrigin.USUARIO,
                destinatarios
        );
    }

    @Override
    public void atualizada(Long hospedagemId, String codigo, String autorNome,
                           String hospede, String campo, String de, String para,
                           Long autorId, String autorJson, Set<Long> destinatarios) {

        String titulo   = tituloAtualizada();
        String mensagem = msgAtualizada(hospedagemId, codigo, autorNome, hospede, campo, de, para);
        String body     = mensagem;

        notifications.send(
                NotificationType.HOSPEDAGEM_ATUALIZADA,
                titulo,
                body,
                "/hospedagens/" + hospedagemId,
                "ABRIR_HOSPEDAGEM",
                hospedagemId,
                today(),
                autorId,
                autorJson,
                NotificationOrigin.USUARIO,
                destinatarios
        );
    }

    @Override
    public void checkoutManual(Long hospedagemId, String codigo, String autorNome,
                               String hospede, String quarto, String dataSaida, String motivo,
                               Long autorId, String autorJson, Set<Long> destinatarios) {

        String titulo   = tituloCheckoutManual();
        String mensagem = msgCheckoutManual(hospedagemId, codigo, autorNome, hospede, quarto, dataSaida, motivo);
        String body     = mensagem;

        notifications.send(
                NotificationType.HOSPEDAGEM_CHECKOUT_MANUAL,
                titulo,
                body,
                "/hospedagens/" + hospedagemId,
                "ABRIR_HOSPEDAGEM",
                hospedagemId,
                today(),
                autorId,
                autorJson,
                NotificationOrigin.USUARIO,
                destinatarios
        );
    }

    @Override
    public void checkoutLembrete11h(Long hospedagemId, String codigo,
                                    String hospede, String quarto, String dataSaida,
                                    Set<Long> destinatarios) {

        String titulo   = tituloLembreteCheckout11h();
        String mensagem = msgLembrete11h(hospedagemId, codigo, hospede, quarto, dataSaida);
        String body     = mensagem;

        notifications.send(
                NotificationType.HOSPEDAGEM_CHECKOUT_LEMBRETE_11H,
                titulo,
                body,
                "/hospedagens/" + hospedagemId,
                "ABRIR_HOSPEDAGEM",
                hospedagemId,
                today(),
                null,
                null,
                NotificationOrigin.AUTOMATICO,
                destinatarios
        );
    }

    @Override
    public void checkoutAutomaticoConcluido(Long hospedagemId, String codigo,
                                            String hospede, String quarto, String dataSaida,
                                            Set<Long> destinatarios) {

        String titulo   = tituloCheckoutAutomatico();
        String mensagem = msgCheckoutAuto(hospedagemId, codigo, hospede, quarto, dataSaida);
        String body     = mensagem;

        notifications.send(
                NotificationType.HOSPEDAGEM_CHECKOUT_AUTOMATICO,
                titulo,
                body,
                "/hospedagens/" + hospedagemId,
                "ABRIR_HOSPEDAGEM",
                hospedagemId,
                today(),
                null,
                null,
                NotificationOrigin.AUTOMATICO,
                destinatarios
        );
    }
}