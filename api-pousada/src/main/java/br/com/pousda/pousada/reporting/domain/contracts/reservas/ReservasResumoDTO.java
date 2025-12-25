package br.com.pousda.pousada.reporting.domain.contracts.reservas;

import lombok.Data;

@Data
public class ReservasResumoDTO {
    private long pendentes;
    private long confirmadas;
    private long canceladas;
    // % (0..100 com duas casas)
    private double taxaConversao;
}
