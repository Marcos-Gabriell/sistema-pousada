package br.com.pousda.pousada.notificacoes.core.formatter;

import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;
import org.springframework.stereotype.Component;

@Component
public class DefaultNotificationFormatter implements NotificationFormatter {
    @Override public boolean supports(NotificationType type) { return false; }
    @Override public String format(Notification n) {
        return n.getBody() == null ? "-" : n.getBody().trim();
    }
}
