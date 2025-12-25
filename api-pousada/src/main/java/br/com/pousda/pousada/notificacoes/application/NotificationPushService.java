package br.com.pousda.pousada.notificacoes.application;


import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationPushService {
    SseEmitter subscribe(long userId);
    void publish(long userId, Object payload);
    void publishAll(Object payload);
}
