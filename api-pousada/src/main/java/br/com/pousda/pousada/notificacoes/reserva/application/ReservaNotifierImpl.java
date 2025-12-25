package br.com.pousda.pousada.notificacoes.reserva.application;

import br.com.pousda.pousada.notificacoes.application.NotificationService;
import br.com.pousda.pousada.notificacoes.application.Templates;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationOrigin;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReservaNotifierImpl implements ReservaNotifier {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final String SEP = ";;;";

    private final NotificationService notifications;
    private final Templates tpl;

    private String today() {
        return LocalDate.now(SP).toString();
    }

    /**
     * Cria o JSON do "Alvo" (a Reserva) - Garantindo que o código seja sempre incluído
     */
    private String targetJsonOf(Long id, String codigo, String hospede, String quarto) {
        if (id == null) return "{}";

        // Garantir que o código seja sempre incluído
        String c = (codigo == null || codigo.trim().isEmpty()) ? "-" : codigo.trim();
        String h = (hospede == null || hospede.trim().isEmpty()) ? "-" : hospede.trim();
        String q = (quarto == null || quarto.trim().isEmpty()) ? "-" : quarto.trim(); // 🔽 Quarto incluído

        // Escapa aspas
        c = c.replace("\"", "\\\"");
        h = h.replace("\"", "\\\"");
        q = q.replace("\"", "\\\"");

        return "{\"id\":" + id + ",\"codigo\":\"" + c + "\",\"hospede\":\"" + h + "\",\"quarto\":\"" + q + "\"}";
    }

    @Override
    public void criada(Long reservaId,
                       String codigo,
                       String hospede,
                       String quarto,
                       String periodo,
                       Long autorId,
                       String autorJson,
                       Set<Long> destinatarios) {

        String jsonAlvo = targetJsonOf(reservaId, codigo, hospede, quarto);

        // Mensagem neutra (sem autor)
        String texto = "Período: " + (periodo == null ? "-" : periodo);

        String body = jsonAlvo + SEP + texto;

        notifications.send(
                NotificationType.RESERVA_CRIADA,
                "Nova reserva criada",
                body,
                "/reservas/" + reservaId,
                "ABRIR_RESERVA",
                reservaId,
                today(),
                autorId,
                autorJson,
                NotificationOrigin.USUARIO,
                destinatarios
        );
    }

    @Override
    public void atualizada(Long reservaId,
                           String codigo,
                           String campo,
                           String de,
                           String para,
                           Long autorId,
                           String autorJson,
                           Set<Long> destinatarios) {

        String jsonAlvo = targetJsonOf(reservaId, codigo, null, null);

        // Mensagem neutra (o diff puro)
        String diff = (para == null || para.isBlank() || "-".equals(para)) ? "dados atualizados" : para;

        String body = jsonAlvo + SEP + diff;

        notifications.send(
                NotificationType.RESERVA_ATUALIZADA,
                "Reserva atualizada",
                body,
                "/reservas/" + reservaId,
                "ABRIR_RESERVA",
                reservaId,
                today(),
                autorId,
                autorJson,
                NotificationOrigin.USUARIO,
                destinatarios
        );
    }

    @Override
    public void confirmada(Long reservaId,
                           String codigo,
                           String hospede,
                           Long autorId,
                           String autorJson,
                           Set<Long> destinatarios) {

        String jsonAlvo = targetJsonOf(reservaId, codigo, hospede, null);

        // Mensagem neutra (só o hóspede)
        String texto = "Hóspede: " + (hospede != null ? hospede : "-");

        String body = jsonAlvo + SEP + texto;

        notifications.send(
                NotificationType.RESERVA_CONFIRMADA,
                "Reserva confirmada",
                body,
                "/reservas/" + reservaId,
                "ABRIR_RESERVA",
                reservaId,
                today(),
                autorId,
                autorJson,
                NotificationOrigin.USUARIO,
                destinatarios
        );
    }

    @Override
    public void cancelada(Long reservaId,
                          String codigo,
                          String motivo,
                          Long autorId,
                          String autorJson,
                          Set<Long> destinatarios) {

        String jsonAlvo = targetJsonOf(reservaId, codigo, null, null);

        // Mensagem neutra (só o motivo)
        String mot = (motivo == null || motivo.isBlank()) ? "-" : motivo.trim();
        String texto = "Motivo: " + mot;

        String body = jsonAlvo + SEP + texto;

        notifications.send(
                NotificationType.RESERVA_CANCELADA,
                "Reserva cancelada",
                body,
                "/reservas/" + reservaId,
                "ABRIR_RESERVA",
                reservaId,
                today(),
                autorId,
                autorJson,
                NotificationOrigin.USUARIO,
                destinatarios
        );
    }

    // --- MÉTODOS DE JOB (Automáticos) - Continuam como antes ---

    @Override
    public void resumoVespera(int qtd, String listaQuartos, Set<Long> destinatarios) {
        notifications.send(
                NotificationType.RESERVA_RESUMO_VESPERA,
                "Resumo da véspera",
                tpl.reservaResumoVespera(qtd, listaQuartos),
                "/reservas?filtro=amanha",
                "VER_RESERVAS",
                null,
                today(),
                null,
                null,
                NotificationOrigin.AUTOMATICO,
                destinatarios
        );
    }

    @Override
    public void hojePendente(int qtd, String listaQuartos, Set<Long> destinatarios) {
        notifications.send(
                NotificationType.RESERVA_HOJE_PENDENTE,
                "Check-ins PENDENTES hoje",
                tpl.reservaHojePendente(qtd, listaQuartos),
                "/reservas?filtro=hoje&status=PENDENTE",
                "VER_RESERVAS",
                null,
                today(),
                null,
                null,
                NotificationOrigin.AUTOMATICO,
                destinatarios
        );
    }

    @Override
    public void ultimaChamada(Long reservaId, String codigo, String hospede, String quarto, Set<Long> destinatarios) {
        notifications.send(
                NotificationType.RESERVA_ULTIMA_CHAMADA,
                "Última chamada de confirmação",
                tpl.reservaUltimaChamada(codigo, hospede, quarto),
                "/reservas/" + reservaId,
                "ABRIR_RESERVA",
                reservaId,
                today(),
                null,
                null,
                NotificationOrigin.AUTOMATICO,
                destinatarios
        );
    }

    @Override
    public void ultimaChamadaLote(int qtd, Set<Long> destinatarios) {
        notifications.send(
                NotificationType.RESERVA_ULTIMA_CHAMADA,
                "Última chamada de confirmação",
                tpl.reservaUltimaChamadaLote(qtd),
                "/reservas?filtro=pendentes",
                "VER_RESERVAS",
                null,
                today(),
                null,
                null,
                NotificationOrigin.AUTOMATICO,
                destinatarios
        );
    }

    @Override
    public void naoConfirmadaCancelada(Long reservaId, String codigo, String hospede, Set<Long> destinatarios) {
        notifications.send(
                NotificationType.RESERVA_NAO_CONFIRMADA_CANCELADA,
                "Reserva cancelada por falta de confirmação",
                tpl.reservaNaoConfirmadaCancelada(codigo, hospede),
                "/reservas/" + reservaId,
                "ABRIR_RESERVA",
                reservaId,
                today(),
                null,
                null,
                NotificationOrigin.AUTOMATICO,
                destinatarios
        );
    }

    @Override
    public void naoConfirmadaCanceladaLote(int qtd, Set<Long> destinatarios) {
        notifications.send(
                NotificationType.RESERVA_NAO_CONFIRMADA_CANCELADA,
                "Reservas canceladas por falta de confirmação",
                tpl.reservaNaoConfirmadaCanceladaLote(qtd),
                "/reservas?filtro=canceladas",
                "VER_RESERVAS",
                null,
                today(),
                null,
                null,
                NotificationOrigin.AUTOMATICO,
                destinatarios
        );
    }
}