// src/main/java/br/com/pousda/pousada/reservas/dtos/ReservaDTO.java
package br.com.pousda.pousada.reservas.dtos;

import br.com.pousda.pousada.reservas.domain.TipoCliente;
import lombok.Data;

import javax.validation.constraints.*;
import java.time.LocalDate;

@Data
public class ReservaDTO {

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotNull(message = "Tipo de cliente é obrigatório")
    private TipoCliente tipoCliente;

    @NotBlank(message = "Número do quarto é obrigatório")
    private String numeroQuarto;

    @NotNull(message = "Data de entrada é obrigatória")
    private LocalDate dataEntrada;

    @NotNull(message = "Número de diárias é obrigatório")
    @Min(value = 1, message = "Número de diárias deve ser maior que zero")
    private Integer numeroDiarias;

    @NotNull(message = "Valor da diária é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor da diária deve ser maior que zero")
    private Double valorDiaria;

    @NotBlank(message = "Forma de pagamento é obrigatória")
    private String formaPagamento;

    // opcionais
    private String telefone;
    private String cpf;
    private String email;
    private String observacoes;
}
