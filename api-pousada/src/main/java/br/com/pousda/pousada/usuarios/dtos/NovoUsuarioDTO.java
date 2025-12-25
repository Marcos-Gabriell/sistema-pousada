package br.com.pousda.pousada.usuarios.dtos;

import lombok.Data;
import javax.validation.constraints.*;

@Data
public class NovoUsuarioDTO {

    @NotBlank(message = "Nome é obrigatório.")
    @Size(max = 50, message = "Nome pode ter no máximo 50 caracteres.")
    private String nome;

    @Size(min = 3, message = "Usuário deve ter ao menos 3 caracteres.")
    private String username;

    @NotBlank(message = "E-mail é obrigatório.")
    @Email(message = "E-mail inválido.")
    private String email;

    @Pattern(regexp = "^$|\\d{11}$", message = "Número deve ter 11 dígitos (DDD+número).")
    private String numero;

    @NotBlank(message = "Role é obrigatória.")
    @Pattern(regexp = "(?i)(ADMIN|GERENTE)", message = "Role inválida. Use 'ADMIN' ou 'GERENTE'.")
    private String role;
}
