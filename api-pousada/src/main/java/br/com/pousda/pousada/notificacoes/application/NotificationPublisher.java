package br.com.pousda.pousada.notificacoes.application;


import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;

public interface NotificationPublisher {
    void publish(Notification notification);
}
