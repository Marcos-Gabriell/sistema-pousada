package br.com.pousda.pousada.reporting.application;


import br.com.pousda.pousada.reporting.application.validation.ReportValidator;
import br.com.pousda.pousada.reporting.domain.contracts.hospedagens.*;
import br.com.pousda.pousada.reporting.infrastructure.readrepo.hospedagens.HospedagensReadDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static br.com.pousda.pousada.reporting.application.Timezones.BAHIA;

@Service
@RequiredArgsConstructor
public class HospedagensReportService {

    private final HospedagensReadDao repo;

    public HospedagensReportDTO gerar(HospedagensFilter f, String geradoPor) {
        ReportValidator.validar(f);

        var rows = repo.listar(f.getDataInicio(), f.getDataFim());
        List<HospedagemLinhaDTO> linhas = new ArrayList<>();

        long ativas = 0, inativas = 0;
        int diariasVendidas = 0;
        BigDecimal somaValorFinalizadas = BigDecimal.ZERO;
        long countFinalizadas = 0;

        for (var r: rows) {
            var linha = new HospedagemLinhaDTO();
            linha.setCodigo((String) r[0]);
            linha.setHospede((String) r[1]);
            linha.setQuarto((String) r[2]);
            linha.setEntrada(((java.sql.Date) r[3]).toLocalDate());
            linha.setSaida(((java.sql.Date) r[4]).toLocalDate());
            linha.setDiarias((Integer) r[5]);
            linha.setCriadoPor((String) r[6]);
            linha.setFormaPagamento((String) r[7]);
            linha.setObservacao((String) r[8]);
            linha.setValor((BigDecimal) r[9]);
            String status = (String) r[10];
            String tipo   = (String) r[11];

            // filtros
            if (!"TODAS".equalsIgnoreCase(f.getStatus())) {
                boolean ativa = "ATIVA".equalsIgnoreCase(status) || "CHECKIN".equalsIgnoreCase(status);
                boolean inativa = "FINALIZADA".equalsIgnoreCase(status) || "CANCELADA".equalsIgnoreCase(status);
                if ("ATIVAS".equalsIgnoreCase(f.getStatus()) && !ativa) continue;
                if ("INATIVAS".equalsIgnoreCase(f.getStatus()) && !inativa) continue;
            }
            if (!"TODOS".equalsIgnoreCase(f.getTipo()) && !f.getTipo().equalsIgnoreCase(tipo)) continue;

            // métricas
            if ("ATIVA".equalsIgnoreCase(status) || "CHECKIN".equalsIgnoreCase(status)) ativas++;
            if ("FINALIZADA".equalsIgnoreCase(status) || "CANCELADA".equalsIgnoreCase(status)) inativas++;
            if (linha.getDiarias() != null) diariasVendidas += linha.getDiarias();
            if ("FINALIZADA".equalsIgnoreCase(status) && linha.getValor() != null) {
                somaValorFinalizadas = somaValorFinalizadas.add(linha.getValor());
                countFinalizadas++;
            }

            linhas.add(linha);
        }

        var resumo = new HospedagensResumoDTO();
        resumo.setTotalAtivas(ativas);
        resumo.setTotalInativas(inativas);
        resumo.setTotal(linhas.size());
        resumo.setDiariasVendidas(diariasVendidas);
        resumo.setTicketMedio(countFinalizadas == 0 ? BigDecimal.ZERO :
                somaValorFinalizadas.divide(BigDecimal.valueOf(countFinalizadas), 2, java.math.RoundingMode.HALF_UP));

        var dto = new HospedagensReportDTO();
        dto.setDataInicio(f.getDataInicio());
        dto.setDataFim(f.getDataFim());
        dto.setGeradoEm(LocalDateTime.now(BAHIA));
        dto.setGeradoPor(geradoPor);
        dto.setResumo(resumo);
        dto.setLinhas(linhas);
        return dto;
    }
}
