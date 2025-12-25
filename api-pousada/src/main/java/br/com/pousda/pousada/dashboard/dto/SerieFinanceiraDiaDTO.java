package br.com.pousda.pousada.dashboard.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SerieFinanceiraDiaDTO {
    private LocalDate data;
    private Double totalEntradas;
    private Double totalSaidas;
}
