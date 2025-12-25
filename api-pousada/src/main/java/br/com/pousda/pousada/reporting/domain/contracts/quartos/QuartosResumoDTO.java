package br.com.pousda.pousada.reporting.domain.contracts.quartos;

import lombok.Data;

// QuartosResumoDTO
@Data
public class QuartosResumoDTO {
    private int totalQuartos;
    private Double ocupacaoMedia;      // em %
    private String maisOcupado;

    // Getter adicional para compat com template antigo:
    public String getQuartoMaisOcupado() {
        return maisOcupado;
    }
}
