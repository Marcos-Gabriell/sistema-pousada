package br.com.pousda.pousada.usuarios.dtos;

import java.time.LocalDateTime;

public class UsuarioResponseDTO {

    private Long id;
    private String codigo;
    private String username;
    private String email;
    private String numero;
    private String nome;
    private String role;
    private boolean ativo;
    private boolean bootstrapAdmin;
    private LocalDateTime criadoEm;
    private boolean mustChangePassword;
    private String pwdChangeReason;
    private LocalDateTime lastPasswordChangeAt;
    private LocalDateTime inativadoEm;

    private String avatarMode;
    private String avatarColor;
    private String avatarGradient;
    private Long avatarVersion;

    private String tema;

    private Long criadoPorId;
    private String criadoPorNome;
    private Integer diasParaExclusao;

    // ✅ campos que seu construtor já recebe
    private LocalDateTime ultimoLoginEm;
    private String ultimoLoginIp;

    // ✅ presença
    private Boolean online;

    public UsuarioResponseDTO() {}

    public UsuarioResponseDTO(
            Long id,
            String codigo,
            String username,
            String email,
            String numero,
            String nome,
            String role,
            boolean ativo,
            boolean bootstrapAdmin,
            LocalDateTime criadoEm,
            boolean mustChangePassword,
            String pwdChangeReason,
            LocalDateTime lastPasswordChangeAt,
            LocalDateTime inativadoEm,
            String avatarMode,
            String avatarColor,
            String avatarGradient,
            Long avatarVersion,
            String tema,
            Long criadoPorId,
            String criadoPorNome,
            Integer diasParaExclusao,
            LocalDateTime ultimoLoginEm,
            String ultimoLoginIp
    ) {
        this.id = id;
        this.codigo = codigo;
        this.username = username;
        this.email = email;
        this.numero = numero;
        this.nome = nome;
        this.role = role;
        this.ativo = ativo;
        this.bootstrapAdmin = bootstrapAdmin;
        this.criadoEm = criadoEm;
        this.mustChangePassword = mustChangePassword;
        this.pwdChangeReason = pwdChangeReason;
        this.lastPasswordChangeAt = lastPasswordChangeAt;
        this.inativadoEm = inativadoEm;
        this.avatarMode = avatarMode;
        this.avatarColor = avatarColor;
        this.avatarGradient = avatarGradient;
        this.avatarVersion = avatarVersion;
        this.tema = tema;
        this.criadoPorId = criadoPorId;
        this.criadoPorNome = criadoPorNome;
        this.diasParaExclusao = diasParaExclusao;

        // ✅ agora existe na classe
        this.ultimoLoginEm = ultimoLoginEm;
        this.ultimoLoginIp = ultimoLoginIp;

        // ⚠️ online NÃO vem no construtor (vem via setOnline no service)
        this.online = null;
    }

    // GETTERS e SETTERS

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public boolean isBootstrapAdmin() { return bootstrapAdmin; }
    public void setBootstrapAdmin(boolean bootstrapAdmin) { this.bootstrapAdmin = bootstrapAdmin; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public String getPwdChangeReason() { return pwdChangeReason; }
    public void setPwdChangeReason(String pwdChangeReason) { this.pwdChangeReason = pwdChangeReason; }

    public LocalDateTime getLastPasswordChangeAt() { return lastPasswordChangeAt; }
    public void setLastPasswordChangeAt(LocalDateTime lastPasswordChangeAt) { this.lastPasswordChangeAt = lastPasswordChangeAt; }

    public LocalDateTime getInativadoEm() { return inativadoEm; }
    public void setInativadoEm(LocalDateTime inativadoEm) { this.inativadoEm = inativadoEm; }

    public String getAvatarMode() { return avatarMode; }
    public void setAvatarMode(String avatarMode) { this.avatarMode = avatarMode; }

    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String avatarColor) { this.avatarColor = avatarColor; }

    public String getAvatarGradient() { return avatarGradient; }
    public void setAvatarGradient(String avatarGradient) { this.avatarGradient = avatarGradient; }

    public Long getAvatarVersion() { return avatarVersion; }
    public void setAvatarVersion(Long avatarVersion) { this.avatarVersion = avatarVersion; }

    public String getTema() { return tema; }
    public void setTema(String tema) { this.tema = tema; }

    public Long getCriadoPorId() { return criadoPorId; }
    public void setCriadoPorId(Long criadoPorId) { this.criadoPorId = criadoPorId; }

    public String getCriadoPorNome() { return criadoPorNome; }
    public void setCriadoPorNome(String criadoPorNome) { this.criadoPorNome = criadoPorNome; }

    public Integer getDiasParaExclusao() { return diasParaExclusao; }
    public void setDiasParaExclusao(Integer diasParaExclusao) { this.diasParaExclusao = diasParaExclusao; }

    public LocalDateTime getUltimoLoginEm() { return ultimoLoginEm; }
    public void setUltimoLoginEm(LocalDateTime ultimoLoginEm) { this.ultimoLoginEm = ultimoLoginEm; }

    public String getUltimoLoginIp() { return ultimoLoginIp; }
    public void setUltimoLoginIp(String ultimoLoginIp) { this.ultimoLoginIp = ultimoLoginIp; }

    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }
}
