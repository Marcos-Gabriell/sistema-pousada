package br.com.pousda.pousada.reporting.domain.contracts.common;

import java.time.LocalDate;
import lombok.Data;

@Data
public class PeriodoFilter {
    private LocalDate dataInicio;
    private LocalDate dataFim;
}
