package br.com.pousda.pousada.usuarios.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {

    private Long id;
    private String nome;
    private String username;
    private String email;
    private String numero;
    private String role;
    private boolean ativo;

    private String tema;
}
