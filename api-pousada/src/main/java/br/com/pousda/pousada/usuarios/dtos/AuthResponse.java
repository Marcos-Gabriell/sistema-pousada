package br.com.pousda.pousada.usuarios.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String token;
    private Instant tokenExpiresAt;

    private String refreshToken;
    private Instant refreshTokenExpiresAt;

    private Long userId;
    private String role;
    private String username;
    private String email;
    private String telefone;
    private String nome;

    private boolean mustChangePassword;
    private String pwdChangeReason;

    private String tema;

    private LocalDateTime ultimoLoginEm;
    private String ultimoLoginIp;
    private String ipAtual;


    private Boolean online;

    public AuthResponse(
            String token,
            String role,
            String username,
            String email,
            String telefone,
            String nome,
            boolean mustChangePassword,
            String pwdChangeReason
    ) {
        this(token, role, username, email, telefone, nome, mustChangePassword, pwdChangeReason, null);
    }


    public AuthResponse(
            String token,
            String role,
            String username,
            String email,
            String telefone,
            String nome,
            boolean mustChangePassword,
            String pwdChangeReason,
            String tema
    ) {
        this.token = token;
        this.role = role;
        this.username = username;
        this.email = email;
        this.telefone = telefone;
        this.nome = nome;
        this.mustChangePassword = mustChangePassword;
        this.pwdChangeReason = pwdChangeReason;
        this.tema = tema;
    }

    public static AuthResponse forLogin(
            Long userId,
            String role,
            String username,
            String email,
            String telefone,
            String nome,
            boolean mustChangePassword,
            String pwdChangeReason,
            String tema,
            String token,
            Instant tokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
        AuthResponse r = new AuthResponse(
                token, role, username, email, telefone, nome, mustChangePassword, pwdChangeReason, tema
        );
        r.setUserId(userId);
        r.setTokenExpiresAt(tokenExpiresAt);
        r.setRefreshToken(refreshToken);
        r.setRefreshTokenExpiresAt(refreshTokenExpiresAt);
        return r;
    }

    public static AuthResponse forMe(
            Long userId,
            String role,
            String username,
            String email,
            String telefone,
            String nome,
            boolean mustChangePassword,
            String pwdChangeReason,
            String tema
    ) {
        AuthResponse r = new AuthResponse(
                null, role, username, email, telefone, nome, mustChangePassword, pwdChangeReason, tema
        );
        r.setUserId(userId);
        return r;
    }
}
