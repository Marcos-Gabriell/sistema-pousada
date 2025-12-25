package br.com.pousda.pousada.usuarios.dtos;


public class UsuarioCriadoResponse {
    private Long id;
    private String nome;
    private String username;
    private String email;
    private String numero;
    private String role;
    private Boolean ativo;
    private String mensagem;
    private String senhaTemporaria;

    public UsuarioCriadoResponse(Long id, String nome, String username, String email, String numero,
                                 String role, Boolean ativo, String mensagem, String senhaTemporaria) {
        this.id = id;
        this.nome = nome;
        this.username = username;
        this.email = email;
        this.numero = numero;
        this.role = role;
        this.ativo = ativo;
        this.mensagem = mensagem;
        this.senhaTemporaria = senhaTemporaria;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getNumero() { return numero; }
    public String getRole() { return role; }
    public Boolean getAtivo() { return ativo; }
    public String getMensagem() { return mensagem; }
    public String getSenhaTemporaria() { return senhaTemporaria; }
}
