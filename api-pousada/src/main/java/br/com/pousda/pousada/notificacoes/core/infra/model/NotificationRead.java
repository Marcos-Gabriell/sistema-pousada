package br.com.pousda.pousada.notificacoes.core.infra.model;


import br.com.pousda.pousada.notificacoes.core.domain.model.Notification;
import lombok.*;
import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_reads",
        uniqueConstraints = @UniqueConstraint(name = "uk_read_user_notif", columnNames = {"notification_id","user_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationRead {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id")
    private Notification notification;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;
}
