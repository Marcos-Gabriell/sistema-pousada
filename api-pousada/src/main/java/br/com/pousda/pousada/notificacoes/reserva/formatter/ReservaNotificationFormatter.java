package br.com.pousda.pousada.notificacoes.reserva.formatter;

import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;
import br.com.pousda.pousada.notificacoes.core.formatter.NotificationFormatter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

@Component
public class ReservaNotificationFormatter implements NotificationFormatter {

    private static final Set<NotificationType> SUPPORTED = EnumSet.of(
            NotificationType.RESERVA_CRIADA,
            NotificationType.RESERVA_ATUALIZADA,
            NotificationType.RESERVA_CONFIRMADA,
            NotificationType.RESERVA_CANCELADA,
            NotificationType.RESERVA_RESUMO_VESPERA,
            NotificationType.RESERVA_HOJE_PENDENTE,
            NotificationType.RESERVA_ULTIMA_CHAMADA,
            NotificationType.RESERVA_NAO_CONFIRMADA_CANCELADA
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SEP = ";;;";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter BR  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public boolean supports(NotificationType type) {
        return SUPPORTED.contains(type);
    }

    @Override
    public String format(Notification n) {
        String body = n.getBody() == null ? "-" : n.getBody().trim();
        String jsonPart = null;
        String rawPart = body;

        // Lógica para separar o JSON do resto da mensagem
        if (body.startsWith("{") && body.contains(SEP)) {
            String[] parts = body.split(SEP, 2);
            jsonPart = parts[0];
            rawPart = parts.length > 1 ? parts[1] : "";
        } else if (body.startsWith("{")) {
            // Caso especial onde o body é *só* o JSON
            jsonPart = body;
            rawPart = "";
        }

        String autor = autorLabel(n);
        String alvo = targetLabel(n, jsonPart);

        switch (n.getType()) {
            case RESERVA_CRIADA: {
                // 🔽 CORREÇÃO: Inclui o quarto na mensagem
                String quarto = extrairQuartoDoJson(jsonPart);
                String periodoFormatado = formatarDatasSoltas(rawPart);

                return cleanSentence("Reserva " + alvo + " foi criada por " + autor +
                        ". Quarto: " + quarto + ". " + periodoFormatado);
            }

            case RESERVA_ATUALIZADA: {
                // rawPart é o diff "campo: de -> para"
                String diff = rawPart.replace("->", "→");
                return cleanSentence("Reserva " + alvo + " foi atualizada por " + autor + ". " + diff);
            }

            case RESERVA_CONFIRMADA: {
                // rawPart é "Hóspede: Fulano"
                return cleanSentence("Reserva " + alvo + " foi confirmada por " + autor + ".");
            }

            case RESERVA_CANCELADA: {
                // rawPart é "Motivo: Cancelada manualmente"
                return cleanSentence("Reserva " + alvo + " foi cancelada por " + autor + ". " + rawPart);
            }

            // jobs automáticos: não têm autor
            case RESERVA_RESUMO_VESPERA:
            case RESERVA_HOJE_PENDENTE:
            case RESERVA_ULTIMA_CHAMADA:
            case RESERVA_NAO_CONFIRMADA_CANCELADA:
                return body;

            default:
                return body;
        }
    }

    /* ========================== MÉTODOS AUXILIARES ========================== */

    private static String extrairQuartoDoJson(String jsonBodyPart) {
        try {
            if (jsonBodyPart != null && !jsonBodyPart.trim().isEmpty() && jsonBodyPart.trim().startsWith("{")) {
                JsonNode node = MAPPER.readTree(jsonBodyPart);
                if (node.hasNonNull("quarto")) {
                    String quarto = node.get("quarto").asText();
                    if (quarto != null && !quarto.isBlank() && !"-".equals(quarto)) {
                        return quarto;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "-";
    }

    private static String autorLabel(Notification n) {
        try {
            String json = n.getAutorJson();
            if (json != null && !json.trim().isEmpty() && json.startsWith("{")) {
                JsonNode node = MAPPER.readTree(json);
                String nome = node.hasNonNull("nome") ? node.get("nome").asText() : "-";
                long idLong  = node.hasNonNull("id")   ? node.get("id").asLong()   : 0L;
                String idStr = (idLong > 0) ? String.valueOf(idLong) : "-";

                // Se nome e id válidos
                if (!"-".equals(nome) && !"-".equals(idStr)) {
                    return nome + " | " + idStr;
                }
                // Se só nome
                if (!"-".equals(nome)) {
                    return nome;
                }
                // Se só id
                if (!"-".equals(idStr)) {
                    return "ID " + idStr;
                }
            }
        } catch (Exception ignored) {}

        // Fallback se JSON for nulo, inválido ou vazio
        return "(Sistema)";
    }

    private static String targetLabel(Notification n, String jsonBodyPart) {
        try {
            if (jsonBodyPart != null && !jsonBodyPart.trim().isEmpty() && jsonBodyPart.trim().startsWith("{")) {
                JsonNode node = MAPPER.readTree(jsonBodyPart);
                // O alvo principal de uma reserva é o CÓDIGO
                if (node.hasNonNull("codigo")) {
                    String codigo = node.get("codigo").asText();
                    if (codigo != null && !codigo.isBlank() && !"-".equals(codigo)) {
                        return codigo;
                    }
                }
                // Fallback: se não tiver código, usa ID
                if (node.hasNonNull("id")) {
                    long id = node.get("id").asLong();
                    return "ID " + id;
                }
            }
        } catch (Exception ignored) {}

        // Fallback para o ItemID se o JSON falhar
        return "(ID " + (n.getItemId() != null ? n.getItemId() : "-") + ")";
    }

    private String formatarDatasSoltas(String texto) {
        if (texto == null) return "";
        String out = texto;
        out = trocarPrimeiraDataIso(out);
        out = trocarPrimeiraDataIso(out);
        return out;
    }

    private String trocarPrimeiraDataIso(String s) {
        for (int i = 0; i <= s.length() - 10; i++) {
            if (Character.isDigit(s.charAt(i))
                    && Character.isDigit(s.charAt(i+1))
                    && Character.isDigit(s.charAt(i+2))
                    && Character.isDigit(s.charAt(i+3))
                    && s.charAt(i+4) == '-'
                    && Character.isDigit(s.charAt(i+5))
                    && Character.isDigit(s.charAt(i+6))
                    && s.charAt(i+7) == '-'
                    && Character.isDigit(s.charAt(i+8))
                    && Character.isDigit(s.charAt(i+9))) {

                String iso = s.substring(i, i+10);
                String br  = formatarData(iso);
                return s.substring(0, i) + br + s.substring(i+10);
            }
        }
        return s;
    }

    private String formatarData(String iso) {
        try {
            LocalDate d = LocalDate.parse(iso, ISO);
            return d.format(BR);
        } catch (Exception e) {
            return iso;
        }
    }

    private static String cleanSentence(String s) {
        if (s == null) return "-";
        return s.replaceAll("\\s{2,}", " ")
                .replaceAll("\\)\\.", ")")
                .replaceAll("\\|\\s*\\|", "|")
                .trim();
    }
}