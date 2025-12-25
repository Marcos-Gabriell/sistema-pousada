package br.com.pousda.pousada.reporting.domain.contracts.reservas;


import br.com.pousda.pousada.reporting.domain.contracts.common.PeriodoFilter;
import lombok.Data;

@Data
public class ReservasFilter extends PeriodoFilter {
    private String status;
}
