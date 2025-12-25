package br.com.pousda.pousada.usuarios.domain;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "usuario_username_audit",
        indexes = {
                @Index(name = "idx_username_audit_user", columnList = "usuario_id, changed_at")
        }
)
public class UsuarioUsernameAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "old_username", length = 50)
    private String oldUsername;

    @Column(name = "new_username", length = 50, nullable = false)
    private String newUsername;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
