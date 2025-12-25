package br.com.pousda.pousada.notificacoes.core.formatter;

import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;

public interface NotificationFormatter {
    boolean supports(NotificationType type);

    String format(Notification n);
}
