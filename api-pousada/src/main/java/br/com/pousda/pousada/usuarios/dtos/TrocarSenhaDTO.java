package br.com.pousda.pousada.usuarios.dtos;


import javax.validation.constraints.NotBlank;

public class TrocarSenhaDTO {
    @NotBlank
    private String senhaAtual;

    @NotBlank
    private String novaSenha;

    public String getSenhaAtual() { return senhaAtual; }
    public void setSenhaAtual(String senhaAtual) { this.senhaAtual = senhaAtual; }
    public String getNovaSenha() { return novaSenha; }
    public void setNovaSenha(String novaSenha) { this.novaSenha = novaSenha; }
}
