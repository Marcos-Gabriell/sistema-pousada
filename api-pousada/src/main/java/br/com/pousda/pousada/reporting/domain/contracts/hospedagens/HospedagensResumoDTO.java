package br.com.pousda.pousada.reporting.domain.contracts.hospedagens;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class HospedagensResumoDTO {
    private long totalAtivas;
    private long totalInativas;
    private long total;
    private BigDecimal ticketMedio; // receita/qtde finalizadas no período
    private int diariasVendidas;
}
