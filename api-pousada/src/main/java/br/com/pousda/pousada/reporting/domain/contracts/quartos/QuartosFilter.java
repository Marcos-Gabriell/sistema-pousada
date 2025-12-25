package br.com.pousda.pousada.reporting.domain.contracts.quartos;

import br.com.pousda.pousada.reporting.domain.contracts.common.PeriodoFilter;
import lombok.Data;

@Data
public class QuartosFilter extends PeriodoFilter {
    private String status; // DISPONIVEL|OCUPADO|MANUTENCAO|TODOS
    private String tipo;   // INDIVIDUAL|DUPLO|TRIPLO|TODOS
}
