package br.com.pousda.pousada.reporting.domain.contracts.financeiro;


import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FinanceiroLinhaDTO {
    private LocalDateTime data;
    private String descricao;
    private BigDecimal valor;
    private String tipo;   // ENTRADA | SAIDA
    private String autor;
    private String codigo; // referencia
}
