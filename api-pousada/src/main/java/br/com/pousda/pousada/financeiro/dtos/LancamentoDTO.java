package br.com.pousda.pousada.financeiro.dtos;

import br.com.pousda.pousada.financeiro.domain.enuns.OrigemLancamento;
import br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LancamentoDTO {
    private TipoLancamento tipo;
    private OrigemLancamento origem;   // default = MANUAL quando null
    private Long referenciaId;         // opcional
    private LocalDate data;            // opcional; se null = hoje (usado só na criação)
    private Double valor;              // > 0
    private String formaPagamento;     // opcional
    private String descricao;          // opcional
}
