package br.com.pousda.pousada.reporting.domain.contracts.quartos;

import lombok.Data;

@Data
public class QuartoLinhaDTO {
    private String numero;
    private String tipo;          // INDIVIDUAL | DUPLO | TRIPLO | ...
    private Double valorDiaria;
    private String status;        // DISPONIVEL | OCUPADO | MANUTENCAO | ...
}
