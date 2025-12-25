package br.com.pousda.pousada.notificacoes.usuarios.formatter;

import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;
import br.com.pousda.pousada.notificacoes.core.formatter.NotificationFormatter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class UsuarioNotificationFormatter implements NotificationFormatter {

    private static final Set<NotificationType> SUPPORTED = EnumSet.of(
            NotificationType.USUARIO_CRIADO,
            NotificationType.USUARIO_ATUALIZADO,
            NotificationType.USUARIO_STATUS_ALTERADO,
            NotificationType.USUARIO_SENHA_RESETADA,
            NotificationType.USUARIO_AUTO_EXCLUIDO,
            NotificationType.USUARIO_EXCLUIDO,
            NotificationType.USUARIO_ATUALIZOU_PROPRIO_PERFIL,
            NotificationType.USUARIO_SENHA_ATUALIZADA_PELO_PROPRIO,
            NotificationType.USUARIO_EXCLUSAO_EM_DOIS_DIAS,
            NotificationType.USUARIO_EXCLUIDO_AUTOMATICAMENTE,
            NotificationType.USUARIO_SENHA_ATUALIZADA_NO_PRIMEIRO_LOGIN,
            NotificationType.USUARIO_SEUS_DADOS_ATUALIZADOS
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SEP = ";;;"; // Mesmo separador

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
            // Caso especial (ex: USUARIO_CRIADO, USUARIO_AUTO_EXCLUIDO) onde o body é *só* o JSON
            jsonPart = body;
            rawPart = "";
        }
        // Se não começar com "{", jsonPart continua null e rawPart é o body inteiro

        String autor = autorLabel(n);
        String alvo = targetLabel(n, jsonPart); // Passa a parte do JSON para o helper

        switch (n.getType()) {

            case USUARIO_CRIADO: {
                // jsonPart é o body, rawPart é ""
                return cleanSentence("Usuário " + alvo + " foi criado por " + autor + ".");
            }

            case USUARIO_ATUALIZADO: {
                // rawPart é tpl.usuarioAtualizado(mensagemRica)
                return cleanSentence("Usuário " + alvo + " foi atualizado por " + autor + ". " + rawPart);
            }

            case USUARIO_SENHA_RESETADA: {
                return cleanSentence("Senha do usuário " + alvo + " foi resetada por " + autor + ".");
            }

            case USUARIO_STATUS_ALTERADO: {
                // rawPart é tpl.usuarioStatusAlterado(...)
                boolean inativo = rawPart.toUpperCase().contains("INATIVO");
                if (inativo) {
                    return cleanSentence(
                            "Usuário " + alvo + " está INATIVO por " + autor +
                                    ". Após 7 dias inativo, o cadastro do usuário será excluído automaticamente pelo sistema."
                    );
                } else {
                    return cleanSentence("Usuário " + alvo + " foi ATIVO por " + autor + ".");
                }
            }

            case USUARIO_EXCLUIDO: {
                return cleanSentence("Usuário " + alvo + " excluído por " + autor + ".");
            }

            case USUARIO_AUTO_EXCLUIDO: {
                // O autor é o alvo. autorLabel já tem "Nome | ID"
                return cleanSentence("Usuário " + autor + " auto-excluiu-se.");
            }

            case USUARIO_ATUALIZOU_PROPRIO_PERFIL: {
                // Aqui jsonPart é null, rawPart é a mensagem. 'autor' é o usuário.
                String mudancas = extractMudancas(rawPart);
                return cleanSentence("Usuário " + autor + " atualizou o seu próprio perfil. " + mudancas);
            }

            case USUARIO_SENHA_ATUALIZADA_PELO_PROPRIO: {
                return cleanSentence("Usuário " + autor + " alterou sua própria senha.");
            }

            case USUARIO_SENHA_ATUALIZADA_NO_PRIMEIRO_LOGIN: {
                return cleanSentence("Usuário " + autor + " alterou sua própria senha no primeiro login.");
            }

            case USUARIO_EXCLUSAO_EM_DOIS_DIAS: {
                int dias = 2;
                try {
                    // Busca os dias na rawPart
                    Matcher mx = Pattern.compile("diasRestantes\\s*=\\s*(\\d+)").matcher(rawPart);
                    if (mx.find()) {
                        dias = Integer.parseInt(mx.group(1));
                    } else {
                        mx = Pattern.compile("(\\d+)\\s*dias").matcher(rawPart);
                        if (mx.find()) dias = Integer.parseInt(mx.group(1));
                    }
                } catch (Exception ignored) {}

                return cleanSentence("Alerta: Usuário " + alvo + " será excluído automaticamente em " + dias + " dias.");
            }

            case USUARIO_EXCLUIDO_AUTOMATICAMENTE: {
                return cleanSentence("Usuário " + alvo + " foi excluído automaticamente pelo sistema.");
            }

            case USUARIO_SEUS_DADOS_ATUALIZADOS: {
                return cleanSentence("Seus dados (Usuário " + alvo + ") foram atualizados por " + autor + ".");
            }

            default:
                return cleanSentence(rawPart); // Retorna a parte da mensagem
        }
    }

    /* ====================== HELPERS ====================== */

    private static String extractMudancas(String texto) {
        if (texto == null) return "";
        int anchor = indexOfIgnoreCase(texto, "Mudanças:");
        if (anchor >= 0) {
            return texto.substring(anchor).trim();
        }
        if (indexOfIgnoreCase(texto, "atualizou o perfil") == -1) {
            return texto;
        }
        return "";
    }

    // Não precisa mais do 'raw'
    private static String autorLabel(Notification n) {
        try {
            String json = n.getAutorJson();
            if (json != null && !json.trim().isEmpty()) {
                JsonNode node = MAPPER.readTree(json);
                String nome = node.hasNonNull("nome") ? node.get("nome").asText() : "-";
                long idLong  = node.hasNonNull("id")   ? node.get("id").asLong()   : 0L;
                String idStr = (idLong > 0) ? String.valueOf(idLong) : "-";
                return nome + " | " + idStr;
            }
        } catch (Exception ignored) {}

        return (n.getAutorId() != null) ? "- | " + n.getAutorId() : "-";
    }

    // Agora lê a 'jsonBodyPart' que passamos, em vez do 'raw' inteiro
    private static String targetLabel(Notification n, String jsonBodyPart) {
        try {
            if (jsonBodyPart != null && !jsonBodyPart.trim().isEmpty() && jsonBodyPart.trim().startsWith("{")) {
                JsonNode node = MAPPER.readTree(jsonBodyPart);
                String nome = node.hasNonNull("nome") ? node.get("nome").asText() : "-";
                long idLong  = node.hasNonNull("id")   ? node.get("id").asLong()   : 0L;
                String idStr = (idLong > 0) ? String.valueOf(idLong) : "-";
                return nome + " | " + idStr;
            }
        } catch (Exception ignored) {}

        // Fallback para o ItemID se o JSON falhar ou não existir
        return "- | " + (n.getItemId() != null ? n.getItemId() : "-");
    }

    private static String cleanSentence(String s) {
        if (s == null) return "-";
        return s.replaceAll("\\s{2,}", " ")
                .replaceAll("\\)\\.", ")")
                .replaceAll("\\|\\s*\\|", "|")
                .trim();
    }

    private static int indexOfIgnoreCase(String s, String token) {
        if (s == null || token == null) return -1;
        return s.toLowerCase().indexOf(token.toLowerCase());
    }
}