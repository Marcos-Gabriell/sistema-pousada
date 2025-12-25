package br.com.pousda.pousada.usuarios.dtos;

import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UsuarioDetalheDTO {
    private Long id;
    private String nome;
    private String username;
    private String email;
    private String numero;
    private String role;
    private boolean ativo;
    private String tema;

    private LocalDateTime criadoEm;
    private Long criadoPorId;
    private String criadoPorNome;
}
