package br.com.pousda.pousada.reporting.domain.contracts.quartos;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuartosReportDTO {
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private LocalDateTime geradoEm;
    private String geradoPor;

    private QuartosResumoDTO resumo;
    private List<QuartoLinhaDTO> linhas;
}
