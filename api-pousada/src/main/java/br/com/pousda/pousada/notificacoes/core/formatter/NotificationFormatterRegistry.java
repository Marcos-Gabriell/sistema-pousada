package br.com.pousda.pousada.notificacoes.core.formatter;

import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationType;
import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationFormatterRegistry {

    private final List<NotificationFormatter> formatters;

    public NotificationFormatterRegistry(List<NotificationFormatter> formatters) {
        this.formatters = formatters;
    }

    /** Acha um formatter que dá `supports(type)` ou devolve o corpo cru. */
    public String format(Notification n) {
        NotificationType t = n.getType();
        if (t == null) return n.getBody();
        for (NotificationFormatter f : formatters) {
            if (f.supports(t)) {
                return f.format(n);
            }
        }
        return n.getBody();
    }
}
