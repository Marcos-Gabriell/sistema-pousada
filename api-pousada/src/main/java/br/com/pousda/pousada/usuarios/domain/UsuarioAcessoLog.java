package br.com.pousda.pousada.usuarios.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuario_acesso_log",
        indexes = {
                @Index(name = "idx_uacesso_usuario_em", columnList = "usuario_id, acesso_em")
        }
)
public class UsuarioAcessoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // muitos acessos para um usuário
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonIgnore
    private Usuario usuario;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "acesso_em", nullable = false, updatable = false)
    private LocalDateTime acessoEm;

    public static UsuarioAcessoLog of(Usuario usuario, String ip, String userAgent) {
        UsuarioAcessoLog log = new UsuarioAcessoLog();
        log.setUsuario(usuario);
        log.setIp(ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip);
        log.setUserAgent(userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent);
        return log;
    }
}
