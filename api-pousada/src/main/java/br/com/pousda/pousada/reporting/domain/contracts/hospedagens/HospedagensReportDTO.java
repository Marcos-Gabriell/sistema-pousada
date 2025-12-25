package br.com.pousda.pousada.reporting.domain.contracts.hospedagens;


import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HospedagensReportDTO {
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private LocalDateTime geradoEm;
    private String geradoPor;

    private HospedagensResumoDTO resumo;
    private List<HospedagemLinhaDTO> linhas;
}
