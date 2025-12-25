package br.com.pousda.pousada.notificacoes.core.infra.adapter;


import br.com.pousda.pousada.notificacoes.application.NotificationPublisher;
import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPublisher implements NotificationPublisher {

    private final SimpMessagingTemplate ws;

    @Override
    public void publish(Notification n) {
        try {
            ws.convertAndSend("/topic/notifications", n);
            if (n.getRecipients() != null) {
                for (Long uid : n.getRecipients()) {
                    ws.convertAndSend("/user/" + uid + "/queue/notifications", n);
                }
            }
        } catch (Exception e) {
            log.warn("Falha ao publicar WS: {}", e.getMessage());
        }
    }
}
