package br.com.pousda.pousada.reporting.domain.contracts.geral;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class GeralReportDTO {
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private LocalDateTime geradoEm;
    private String geradoPor;

    // Cards
    private long reservasPendentes;
    private long hospedagensAtivas;
    private long quartosDisponiveis;
    private long quartosOcupados;
    private long quartosManutencao;
    private java.math.BigDecimal entradas;
    private java.math.BigDecimal saidas;
    private java.math.BigDecimal saldo;
    private long usuariosAtivos;

    // Mini-lista
    private java.util.List<java.util.Map<String,Object>> ultimasMovimentacoes;
}
