package br.com.pousda.pousada.usuarios.dtos;


import lombok.Data;

@Data
public class CriarUsuarioDTO {
    private String nome;
    private String username;
    private String email;
    private String numero;
    private String role;
}
