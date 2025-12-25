package br.com.pousda.pousada.notificacoes.core.infra.repo;

import br.com.pousda.pousada.notificacoes.core.domain.enums.NotificationStatus;
import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Transactional
    @Modifying
    @Query("delete from Notification n where n.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") Instant now);

    @Query("select n from Notification n " +
            "where :uid member of n.recipients " +
            "and (n.expiresAt is null or n.expiresAt > :now) " +
            "order by n.createdAt desc")
    List<Notification> findActiveForUser(@Param("uid") Long uid, @Param("now") Instant now);

    @Query("select count(n) from Notification n " +
            "where :uid member of n.recipients " +
            "and (n.expiresAt is null or n.expiresAt > :now) " +
            "and n.status = :status")
    int countUnreadActive(@Param("uid") long uid,
                          @Param("now") Instant now,
                          @Param("status") NotificationStatus status);
}
