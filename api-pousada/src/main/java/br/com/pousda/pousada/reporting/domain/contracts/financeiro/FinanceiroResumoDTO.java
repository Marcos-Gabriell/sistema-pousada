package br.com.pousda.pousada.reporting.domain.contracts.financeiro;


import lombok.Data;
import java.math.BigDecimal;

@Data
public class FinanceiroResumoDTO {
    private BigDecimal totalEntradas;
    private BigDecimal totalSaidas;
    private BigDecimal saldo;
}
