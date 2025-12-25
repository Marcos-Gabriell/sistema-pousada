package br.com.pousda.pousada.usuarios.domain;

import br.com.pousda.pousada.usuarios.domain.enums.AvatarMode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import javax.persistence.*;
import javax.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "usuario",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_usuario_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_usuario_email",    columnNames = "email"),
                @UniqueConstraint(name = "uk_usuario_numero",   columnNames = "numero")
        }
)
@Check(constraints =
        "(" +
                "  pwd_change_reason IS NULL " +
                "  OR pwd_change_reason IN ('FIRST_LOGIN','RESET_BY_ADMIN','ACCOUNT_INACTIVATED')" +
                ") AND (" +
                "  (must_change_password = true  AND pwd_change_reason IS NOT NULL) OR " +
                "  (must_change_password = false AND pwd_change_reason IS NULL)" +
                ")"
)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "numero", unique = true)
    private String numero;

    @Size(max = 15)
    @Column(nullable = false, length = 15)
    private String nome;

    @Column(name = "codigo", nullable = false, unique = true, length = 16)
    private String codigo;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "GERENTE";

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private boolean bootstrapAdmin = false;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "pwd_change_reason", length = 20)
    private String pwdChangeReason;

    @Column(name = "last_password_change_at")
    private LocalDateTime lastPasswordChangeAt;

    @Column(name = "inativado_em")
    private LocalDateTime inativadoEm;

    @Transient
    private Integer diasParaExclusao;

    // ===== AVATAR =====
    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_mode", length = 10, nullable = false)
    private AvatarMode avatarMode = AvatarMode.COLOR;

    @Column(name = "avatar_color", length = 7)
    private String avatarColor;

    @Column(name = "avatar_gradient", length = 40)
    private String avatarGradient;

    @Column(name = "avatar_base64", columnDefinition = "TEXT")
    private String avatarBase64;

    @Column(name = "avatar_version")
    private Long avatarVersion = 0L;

    @Column(name = "tema", nullable = false)
    private String tema = "claro";

    // === CRIADO POR ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    @JsonIgnore
    private Usuario criadoPor;

    @Column(name = "criado_por_nome", length = 40)
    private String criadoPorNome;

    // ===== LOGIN =====
    @Column(name = "ultimo_login_em")
    private LocalDateTime ultimoLoginEm;

    @Column(name = "ultimo_login_ip", length = 45)
    private String ultimoLoginIp;

    // ===== ACESSO (para online/offline) =====
    @Column(name = "ultimo_acesso_em")
    private LocalDateTime ultimoAcessoEm;

    @Column(name = "ultimo_acesso_ip", length = 45)
    private String ultimoAcessoIp;

    @PrePersist
    public void gerarCodigoAutomatico() {
        if (this.codigo == null || this.codigo.isBlank()) {
            String prefix = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
            int sufix = new java.util.Random().nextInt(10_000);
            this.codigo = prefix + String.format("%04d", sufix);
        }
    }
}
