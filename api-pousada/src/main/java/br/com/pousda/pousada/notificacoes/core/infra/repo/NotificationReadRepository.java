package br.com.pousda.pousada.notificacoes.core.infra.repo;

import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;
import br.com.pousda.pousada.notificacoes.core.infra.model.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {

    Optional<NotificationRead> findByNotificationAndUserId(Notification n, Long userId);

    List<NotificationRead> findByUserIdAndNotification_ExpiresAtAfter(Long userId, Instant now);

    long deleteByUserIdAndNotification_ExpiresAtBefore(Long userId, Instant now);

    @Query("select r.notification.id from NotificationRead r " +
            "where r.userId = :uid " +
            "and r.notification.id in :ids")
    Set<Long> findIdsLidas(@Param("uid") Long uid, @Param("ids") Collection<Long> ids);
}
