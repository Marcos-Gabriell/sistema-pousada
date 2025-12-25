package br.com.pousda.pousada.reporting.domain.contracts.reservas;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReservasReportDTO {
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private LocalDateTime geradoEm;
    private String geradoPor;
    private ReservasResumoDTO resumo;
    private List<ReservaLinhaDTO> linhas;
}
