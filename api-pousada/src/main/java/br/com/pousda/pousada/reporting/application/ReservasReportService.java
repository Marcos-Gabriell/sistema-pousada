package br.com.pousda.pousada.reporting.application;

import br.com.pousda.pousada.reporting.application.validation.ReportValidator;
import br.com.pousda.pousada.reporting.domain.contracts.reservas.ReservaLinhaDTO;
import br.com.pousda.pousada.reporting.domain.contracts.reservas.ReservasFilter;
import br.com.pousda.pousada.reporting.domain.contracts.reservas.ReservasResumoDTO;
import br.com.pousda.pousada.reporting.domain.contracts.reservas.ReservasReportDTO;
import br.com.pousda.pousada.reporting.infrastructure.readrepo.reservas.ReservasReadDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static br.com.pousda.pousada.reporting.application.Timezones.BAHIA;

@Service
@RequiredArgsConstructor
public class ReservasReportService {

    private final ReservasReadDao repo;

    public ReservasReportDTO gerar(ReservasFilter f, String geradoPor) {
        ReportValidator.validar(f);

        LocalDate ini = f.getDataInicio();
        LocalDate fim = f.getDataFim();

        List<Object[]> rows = repo.listar(ini, fim);
        List<ReservaLinhaDTO> linhas = new ArrayList<>();

        for (Object[] r : rows) {
            String codigo = (String) r[0];
            String hospede = (String) r[1];
            String quarto = (String) r[2];
            LocalDate criada = r[3] != null ? ((java.sql.Date) r[3]).toLocalDate() : null;
            LocalDate checkinPrev = r[4] != null ? ((java.sql.Date) r[4]).toLocalDate() : null;
            String status = (String) r[5];

            // filtro por status (TODAS ignora)
            if (!"TODAS".equalsIgnoreCase(f.getStatus())
                    && !f.getStatus().equalsIgnoreCase(status)) {
                continue;
            }

            ReservaLinhaDTO dto = new ReservaLinhaDTO();
            dto.setCodigo(codigo);
            dto.setHospede(hospede);
            dto.setQuarto(quarto);
            dto.setCriadaEm(criada);
            dto.setCheckinPrevisto(checkinPrev);
            dto.setStatus(status);
            linhas.add(dto);
        }

        long pend = repo.countPendentes(ini, fim);
        long conf = repo.countConfirmadas(ini, fim);
        long canc = repo.countCanceladas(ini, fim);

        ReservasResumoDTO resumo = new ReservasResumoDTO();
        resumo.setPendentes(pend);
        resumo.setConfirmadas(conf);
        resumo.setCanceladas(canc);

        double taxa = (pend == 0L) ? 0.0 : ((double) conf / (double) pend) * 100.0;
        resumo.setTaxaConversao(Math.round(taxa * 100.0) / 100.0);

        ReservasReportDTO out = new ReservasReportDTO();
        out.setDataInicio(ini);
        out.setDataFim(fim);
        out.setGeradoEm(LocalDateTime.now(BAHIA));
        out.setGeradoPor(geradoPor);
        out.setResumo(resumo);
        out.setLinhas(linhas);
        return out;
    }
}
