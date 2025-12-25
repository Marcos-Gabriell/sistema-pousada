package br.com.pousda.pousada.reporting.domain.contracts.financeiro;

import br.com.pousda.pousada.reporting.domain.contracts.common.PeriodoFilter;
import lombok.Data;

@Data
public class FinanceiroFilter extends PeriodoFilter {

    private String tipo;
}
