package br.com.pousda.pousada.reporting.domain.contracts.hospedagens;

import br.com.pousda.pousada.reporting.domain.contracts.common.PeriodoFilter;
import lombok.Data;

@Data
public class HospedagensFilter extends PeriodoFilter {
    private String status; // ATIVAS | INATIVAS | TODAS
    private String tipo;   // COMUM | PREFEITURA | CORPORATIVO | TODOS
}
