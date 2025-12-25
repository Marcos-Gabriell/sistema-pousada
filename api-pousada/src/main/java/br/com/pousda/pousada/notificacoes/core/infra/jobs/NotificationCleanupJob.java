package br.com.pousda.pousada.notificacoes.core.infra.jobs;

import br.com.pousda.pousada.notificacoes.application.NotificationCleanupService;
import br.com.pousda.pousada.notificacoes.core.infra.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupJob {

    private final NotificationCleanupService cleanupService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void removeExpiradas() {
        cleanupService.cleanUpExpiredNotifications();
    }
}