package br.com.pousda.pousada.notificacoes.financeiro.application;

import br.com.pousda.pousada.notificacoes.application.NotificationService;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationOrigin;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinanceiroNotifierImpl implements FinanceiroNotifier {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = new Locale("pt", "BR");

    private final NotificationService notifications;
    private final ObjectMapper mapper;

    private String today() { return LocalDate.now(SP).toString(); }
    private String money(Double v) { return NumberFormat.getCurrencyInstance(PT_BR).format(v != null ? v : 0d); }
    private String safe(String s) { return (s == null || s.isBlank()) ? "-" : s; }

    private String autorDisplay(String autorJson, Long autorId) {
        String nome = "-";
        if (autorJson != null && !autorJson.isBlank()) {
            try {
                JsonNode n = mapper.readTree(autorJson);
                if (n.has("nome") && !n.get("nome").asText().isBlank()) {
                    nome = n.get("nome").asText();
                }
            } catch (Exception ignored) {}
        }
        String id = (autorId == null) ? "-" : String.valueOf(autorId);
        return nome + " | " + id;
    }

    @Override
    public void lancamentoCriado(Long id, String tipo, String origem, Double valor, String descricao,
                                 Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = String.format(
                "%s criada (%s) no valor de %s • %s • por %s",
                tipo, origem, money(valor), safe(descricao), autorDisplay(autorJson, autorId)
        );
        notifications.send(
                NotificationType.valueOf("FIN_LANCAMENTO_CRIADO"),
                "Financeiro: lançamento criado",
                body,
                "/financeiro/lancamentos/" + id,
                "VER_LANCAMENTO",
                id,
                today(),
                autorId,
                autorJson,
                NotificationOrigin.USUARIO,
                destinatarios
        );
    }

    @Override
    public void lancamentoAtualizado(Long id, String tipo, String origem,
                                     String deDescricao, Double deValor,
                                     String paraDescricao, Double paraValor,
                                     Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = String.format(
                "%s (%s) atualizado: \"%s\" (%s) → \"%s\" (%s) • por %s",
                tipo, origem,
                safe(deDescricao), money(deValor),
                safe(paraDescricao), money(paraValor),
                autorDisplay(autorJson, autorId)
        );
        notifications.send(
                NotificationType.valueOf("FIN_LANCAMENTO_ATUALIZADO"),
                "Financeiro: lançamento atualizado",
                body,
                "/financeiro/lancamentos/" + id,
                "VER_LANCAMENTO",
                id,
                today(),
                autorId,
                autorJson,
                NotificationOrigin.USUARIO,
                destinatarios
        );
    }

    @Override
    public void lancamentoCancelado(Long id, String tipo, String origem, Double valor,
                                    String motivo, String canceladoPorPerfil,
                                    Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = String.format(
                "%s (%s) cancelado no valor de %s • Motivo: %s • Perfil: %s • por %s",
                tipo, origem, money(valor), safe(motivo), safe(canceladoPorPerfil),
                autorDisplay(autorJson, autorId)
        );
        notifications.send(
                NotificationType.valueOf("FIN_LANCAMENTO_CANCELADO"),
                "Financeiro: lançamento cancelado",
                body,
                "/financeiro/lancamentos",
                "VER_LANCAMENTOS",
                id,
                today(),
                autorId,
                autorJson,
                NotificationOrigin.USUARIO,
                destinatarios
        );
    }
}
