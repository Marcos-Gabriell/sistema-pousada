package br.com.pousda.pousada.notificacoes.core.domain.model;

import br.com.pousda.pousada.notificacoes.core.domain.enums.*;
import lombok.*;
import javax.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_expires_at", columnList = "expiresAt"),
                @Index(name = "idx_notifications_status",    columnList = "status")
        }
)
public class Notification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private NotificationType type;

    @Column(nullable = false, length = 140)
    private String title;

    // Evita LOB stream no Postgres
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    private String link;
    private String action;
    private Long itemId;
    private String date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private NotificationOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private NotificationStatus status = NotificationStatus.NOVO;

    // ids dos destinatários (EAGER para evitar LazyInitialization ao serializar)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_recipients", joinColumns = @JoinColumn(name = "notification_id"))
    @Column(name = "user_id", nullable = false)
    @Builder.Default
    private Set<Long> recipients = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt; // TTL

    // autor da ação
    private Long   autorId;
    private String autorJson; // ex.: {"id":1,"nome":"DEV","role":"DEV"}

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (expiresAt == null) {
            long dias = 90L * 24L * 60L * 60L;
            expiresAt = createdAt.plusSeconds(dias);
        }
        if (status == null) status = NotificationStatus.NOVO;
        if (recipients == null) recipients = new HashSet<>();
    }
}
