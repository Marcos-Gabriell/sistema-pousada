package br.com.pousda.pousada.notificacoes.quarto.application;

import br.com.pousda.pousada.notificacoes.application.NotificationService;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationOrigin;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import br.com.pousda.pousada.quartos.domain.Quarto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuartoNotifierImpl implements QuartoNotifier {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private final NotificationService notifications;

    private String today() { return LocalDate.now(SP).toString(); }
    private String n(Quarto q)  { return q != null && q.getNumero() != null ? q.getNumero() : "-"; }
    private String nm(Quarto q) { return q != null && q.getNome()   != null ? q.getNome()   : "-"; }

    // ===== helpers: "NOME | ID" =====
    private String autorLabel(Long autorId, String autorJson) {
        String nome = extrairNomeDoAutor(autorJson);
        if (nome == null || nome.isBlank()) nome = "-";
        String idStr = (autorId != null && autorId > 0) ? String.valueOf(autorId) : "-";
        return nome + " | " + idStr;
    }

    // parser leve de {"id":1,"nome":"Fulano"}
    private String extrairNomeDoAutor(String json) {
        if (json == null) return null;
        try {
            int i = json.indexOf("\"nome\"");
            if (i < 0) return null;
            int colon = json.indexOf(':', i);
            int start = json.indexOf('"', colon + 1);
            if (start < 0) return null;
            int end = json.indexOf('"', start + 1);
            if (end < 0) return null;
            return json.substring(start + 1, end);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void criado(Quarto quarto, Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = "Quarto " + n(quarto) + " (" + nm(quarto) + ") foi criado por " + autorLabel(autorId, autorJson) + ".";
        notifications.send(
                NotificationType.QUARTO_CRIADO,
                "Quarto criado",
                body,
                "/quartos/" + quarto.getId(), "VER_QUARTO", quarto.getId(),
                today(), autorId, autorJson, NotificationOrigin.USUARIO, destinatarios
        );
    }

    @Override
    public void atualizado(Quarto quarto, String resumo, Long autorId, String autorJson, Set<Long> destinatarios) {
        String who = autorLabel(autorId, autorJson);
        String body = (resumo == null || resumo.isBlank())
                ? "Dados do quarto " + n(quarto) + " (" + nm(quarto) + ") foram atualizados por " + who + "."
                : "Quarto " + n(quarto) + " (" + nm(quarto) + ") atualizado por " + who + " • " + resumo;
        notifications.send(
                NotificationType.QUARTO_ATUALIZADO,
                "Quarto atualizado",
                body,
                "/quartos/" + quarto.getId(), "VER_QUARTO", quarto.getId(),
                today(), autorId, autorJson, NotificationOrigin.USUARIO, destinatarios
        );
    }

    @Override
    public void entrouManutencao(Quarto quarto, Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = "Quarto " + n(quarto) + " (" + nm(quarto) + ") entrou em manutenção por " + autorLabel(autorId, autorJson) + ".";
        notifications.send(
                NotificationType.QUARTO_ENTROU_MANUTENCAO,
                "Quarto em manutenção",
                body,
                "/quartos/" + quarto.getId(), "VER_QUARTO", quarto.getId(),
                today(), autorId, autorJson, NotificationOrigin.USUARIO, destinatarios
        );
    }

    @Override
    public void voltouDisponivel(Quarto quarto, Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = "Quarto " + n(quarto) + " (" + nm(quarto) + ") voltou a ficar disponível por " + autorLabel(autorId, autorJson) + ".";
        notifications.send(
                NotificationType.QUARTO_VOLTOU_DISPONIVEL,
                "Quarto disponível",
                body,
                "/quartos/" + quarto.getId(), "VER_QUARTO", quarto.getId(),
                today(), autorId, autorJson, NotificationOrigin.USUARIO, destinatarios
        );
    }

    @Override
    public void excluido(Quarto quarto, Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = "Quarto " + n(quarto) + " (" + nm(quarto) + ") foi excluído por " + autorLabel(autorId, autorJson) + ".";
        notifications.send(
                NotificationType.QUARTO_EXCLUIDO,
                "Quarto excluído",
                body,
                "/quartos", "VER_QUARTOS", quarto.getId(),
                today(), autorId, autorJson, NotificationOrigin.USUARIO, destinatarios
        );
    }
}
