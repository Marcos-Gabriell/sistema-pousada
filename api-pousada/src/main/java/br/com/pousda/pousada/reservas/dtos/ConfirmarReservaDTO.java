// src/main/java/br/com/pousda/pousada/reservas/dtos/ConfirmarReservaDTO.java
package br.com.pousda.pousada.reservas.dtos;

import br.com.pousda.pousada.reservas.domain.TipoCliente;
import lombok.Data;

/**
 * Para confirmar, nada é obrigatório.
 * Se vier tipoCliente, atualiza.
 * Se vier observações de check-in, salva.
 */
@Data
public class ConfirmarReservaDTO {
    private TipoCliente tipoCliente;
    private String observacoesCheckin;
}
