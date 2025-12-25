package br.com.pousda.pousada.notificacoes.application;


import br.com.pousda.pousada.notificacoes.core.infra.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCleanupService {

    private final NotificationRepository repo;

    @Transactional
    public void cleanUpExpiredNotifications() {
        try {
            LocalDateTime now = LocalDateTime.now();
            log.info("Iniciando limpeza de notificações expiradas antes de: {}", now);

            int deletedCount = repo.deleteByExpiresAtBefore(Instant.now());


            log.info("Limpeza concluída: {} notificações expiradas removidas", deletedCount);
        } catch (Exception e) {
            log.error("Erro durante a limpeza de notificações expiradas", e);
            throw e; // Re-lança a exceção para que a transação seja revertida
        }
    }
}