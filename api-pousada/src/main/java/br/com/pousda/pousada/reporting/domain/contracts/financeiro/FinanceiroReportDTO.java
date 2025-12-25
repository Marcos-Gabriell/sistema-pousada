package br.com.pousda.pousada.reporting.domain.contracts.financeiro;


import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FinanceiroReportDTO {
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private LocalDateTime geradoEm;
    private String geradoPor;

    private FinanceiroResumoDTO resumo;
    private List<FinanceiroLinhaDTO> linhas;
}
