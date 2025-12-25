package br.com.pousda.pousada.reporting.domain.contracts.hospedagens;

import lombok.Data;
import java.time.LocalDate;
import java.math.BigDecimal;

@Data
public class HospedagemLinhaDTO {
    private String hospede;
    private String quarto;
    private Integer diarias;
    private LocalDate entrada;
    private LocalDate saida;
    private String codigo;
    private String criadoPor;
    private String formaPagamento;
    private String observacao;
    private BigDecimal valor; // opcional: total da hospedagem
}
