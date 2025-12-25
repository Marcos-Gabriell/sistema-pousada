package br.com.pousda.pousada.usuarios.dtos;


import lombok.Data;

@Data
public class AtualizarProprioUsuarioDTO {
    private String nome;
    private String email;
    private String numero;
    private String username;
}