package br.com.pousda.pousada.notificacoes.usuarios.application;

import br.com.pousda.pousada.notificacoes.application.NotificationService;
import br.com.pousda.pousada.notificacoes.application.Templates;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationOrigin;
import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioNotifierImpl implements UsuarioNotifier {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final String SEP = ";;;";
    private final NotificationService notifications;
    private final Templates tpl;

    private String today() { return LocalDate.now(SP).toString(); }
    private String showLink(Long id) { return id == null ? "/usuarios" : "/usuarios/" + id; }
    private String actionShow(Long id) { return id == null ? "VER_USUARIOS" : "ABRIR_USUARIO"; }
    private Set<Long> safeRecipients(Set<Long> r) { return Objects.requireNonNullElse(r, Set.of()); }

    private String autorJsonOf(Long id, String nome) {
        if (id == null) return "{}";
        String n = (nome == null || nome.trim().isEmpty()) ? "-" : nome.trim();
        return "{\"id\":" + id + ",\"nome\":\"" + n.replace("\"","\\\"") + "\"}";
    }

    @Override
    public void criado(Long usuarioId, String nome, String email, Long autorId, String autorJson, Set<Long> destinatarios) {
        String targetJsonAsBody = autorJsonOf(usuarioId, nome);

        notifications.send(
                NotificationType.USUARIO_CRIADO,
                "Usuário criado",
                targetJsonAsBody,
                showLink(usuarioId), actionShow(usuarioId),
                usuarioId, today(), autorId, autorJson,
                NotificationOrigin.USUARIO,
                safeRecipients(destinatarios)
        );
    }

    @Override
    public void atualizado(Long usuarioId, String nome, String mensagemRica, Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = autorJsonOf(usuarioId, nome) + SEP + tpl.usuarioAtualizado(mensagemRica);

        notifications.send(
                NotificationType.USUARIO_ATUALIZADO,
                "Usuário atualizado",
                body,
                showLink(usuarioId), actionShow(usuarioId),
                usuarioId, today(), autorId, autorJson,
                NotificationOrigin.USUARIO,
                safeRecipients(destinatarios)
        );
    }

    @Override
    public void statusAlterado(Long usuarioId, String nome, String novoStatus, Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = autorJsonOf(usuarioId, nome) + SEP + tpl.usuarioStatusAlterado(usuarioId, nome, novoStatus);

        notifications.send(
                NotificationType.USUARIO_STATUS_ALTERADO,
                "Status de usuário alterado",
                body,
                showLink(usuarioId), actionShow(usuarioId),
                usuarioId, today(), autorId, autorJson,
                NotificationOrigin.USUARIO,
                safeRecipients(destinatarios)
        );
    }

    @Override
    public void senhaResetada(Long usuarioId, String nome, Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = autorJsonOf(usuarioId, nome) + SEP + tpl.usuarioSenhaResetada(usuarioId, nome);

        notifications.send(
                NotificationType.USUARIO_SENHA_RESETADA,
                "Senha resetada",
                body,
                showLink(usuarioId), actionShow(usuarioId),
                usuarioId, today(), autorId, autorJson,
                NotificationOrigin.USUARIO,
                safeRecipients(destinatarios)
        );
    }

    @Override
    public void autoExcluido(Long usuarioId, String nome, Set<Long> destinatarios) {
        String autorJson = autorJsonOf(usuarioId, nome);
        // O body é apenas o JSON, pois o autor e o alvo são os mesmos
        String body = autorJsonOf(usuarioId, nome);

        notifications.send(
                NotificationType.USUARIO_AUTO_EXCLUIDO,
                "Usuário auto-excluído",
                body,
                "/usuarios", "VER_USUARIOS",
                usuarioId, today(), usuarioId, autorJson,
                NotificationOrigin.AUTOMATICO,
                safeRecipients(destinatarios)
        );
    }

    @Override
    public void excluido(Long usuarioId, String nome, Long autorId, String autorJson, Set<Long> destinatarios) {
        String body = autorJsonOf(usuarioId, nome) + SEP + tpl.usuarioExcluido(usuarioId, nome);

        notifications.send(
                NotificationType.USUARIO_EXCLUIDO,
                "Usuário excluído",
                body,
                "/usuarios", "VER_USUARIOS",
                usuarioId, today(), autorId, autorJson,
                NotificationOrigin.USUARIO,
                safeRecipients(destinatarios)
        );
    }

    @Override
    public void atualizouProprioPerfil(Long usuarioId, String nome, String campo, String de, String para, Set<Long> destinatarios) {
        String autorJson = autorJsonOf(usuarioId, nome);
        // Esta notificação não precisa do JSON do alvo no body, pois o "autor" é o alvo.
        String mensagem = "Mudanças: " + campo + ": " + de + " → " + para;

        Set<Long> destinatariosFiltrados = safeRecipients(destinatarios).stream()
                .filter(id -> !id.equals(usuarioId))
                .collect(Collectors.toSet());
        notifications.send(
                NotificationType.USUARIO_ATUALIZOU_PROPRIO_PERFIL,
                "Perfil atualizado",
                mensagem,
                showLink(usuarioId), actionShow(usuarioId),
                usuarioId, today(), usuarioId, autorJson,
                NotificationOrigin.USUARIO,
                destinatariosFiltrados
        );
    }

    @Override
    public void senhaAtualizadaPeloProprio(Long usuarioId, String nome, Set<Long> destinatarios) {
        String autorJson = autorJsonOf(usuarioId, nome);
        // Esta notificação não precisa do JSON do alvo no body, pois o "autor" é o alvo.
        Set<Long> destinatariosFiltrados = safeRecipients(destinatarios).stream()
                .filter(id -> !id.equals(usuarioId))
                .collect(Collectors.toSet());
        notifications.send(
                NotificationType.USUARIO_SENHA_ATUALIZADA_PELO_PROPRIO,
                "Senha atualizada",
                "",
                showLink(usuarioId), actionShow(usuarioId),
                usuarioId, today(), usuarioId, autorJson,
                NotificationOrigin.USUARIO,
                destinatariosFiltrados
        );
    }

    @Override
    public void senhaAtualizadaNoPrimeiroLogin(Long usuarioId, String nome, Set<Long> destinatarios) {
        String autorJson = autorJsonOf(usuarioId, nome);
        // O formatador para esta notificação não utiliza o body, apenas o autor.
        Set<Long> destinatariosFiltrados = safeRecipients(destinatarios).stream()
                .filter(id -> !id.equals(usuarioId))
                .collect(Collectors.toSet());

        notifications.send(
                NotificationType.USUARIO_SENHA_ATUALIZADA_NO_PRIMEIRO_LOGIN,
                "Senha atualizada (1º login)",
                "", // O body deve ser vazio.
                showLink(usuarioId), actionShow(usuarioId),
                usuarioId, today(), usuarioId, autorJson,
                NotificationOrigin.USUARIO,
                destinatariosFiltrados
        );
    }

    @Override
    public void exclusaoEmDias(Long usuarioId, String nome, int diasRestantes, Set<Long> destinatarios) {
        String titulo = "Aviso: exclusão em " + diasRestantes + " dias";
        // Adiciona o JSON do alvo + SEP
        String body   = autorJsonOf(usuarioId, nome) + SEP + "Usuário " + nome + " será excluído em " + diasRestantes + " dias. [diasRestantes=" + diasRestantes + "]";

        notifications.send(
                NotificationType.USUARIO_EXCLUSAO_EM_DOIS_DIAS,
                titulo,
                body,
                showLink(usuarioId), actionShow(usuarioId),
                usuarioId, today(), null, null,
                NotificationOrigin.AUTOMATICO,
                safeRecipients(destinatarios)
        );
    }

    @Override
    public void exclusaoEmDoisDias(Long usuarioId, String nome, Set<Long> destinatarios) {
        exclusaoEmDias(usuarioId, nome, 2, destinatarios);
    }

    @Override
    public void excluidoAutomaticamente(Long usuarioId, String nome, Set<Long> destinatarios) {
        // Adiciona o JSON do alvo + SEP
        String body = autorJsonOf(usuarioId, nome) + SEP + "Usuário " + nome + " foi excluído. [diasRestantes=0]";

        notifications.send(
                NotificationType.USUARIO_EXCLUIDO_AUTOMATICAMENTE,
                "Usuário excluído automaticamente",
                body,
                "/usuarios", "VER_USUARIOS",
                usuarioId, today(), null, null,
                NotificationOrigin.AUTOMATICO,
                safeRecipients(destinatarios)
        );
    }

    @Override
    public void seusDadosAtualizados(Long usuarioId, String nome, String mudancas, Long autorId, String autorJson) {
        // Esta é uma notificação *para* o usuário-alvo.
        // O "autor" é quem atualizou, o "alvo" é o próprio usuário.
        String body = autorJsonOf(usuarioId, nome) + SEP + tpl.usuarioSeusDadosAtualizados(nome, mudancas);

        notifications.send(
                NotificationType.USUARIO_SEUS_DADOS_ATUALIZADOS,
                "Seus dados foram atualizados",
                body,
                showLink(usuarioId), actionShow(usuarioId),
                usuarioId, today(), autorId, autorJson,
                NotificationOrigin.USUARIO,
                Set.of(usuarioId)
        );
    }
}