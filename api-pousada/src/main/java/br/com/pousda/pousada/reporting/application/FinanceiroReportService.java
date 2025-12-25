package br.com.pousda.pousada.reporting.application;

import br.com.pousda.pousada.reporting.application.validation.ReportValidator;
import br.com.pousda.pousada.reporting.domain.contracts.financeiro.FinanceiroFilter;
import br.com.pousda.pousada.reporting.domain.contracts.financeiro.FinanceiroLinhaDTO;
import br.com.pousda.pousada.reporting.domain.contracts.financeiro.FinanceiroReportDTO;
import br.com.pousda.pousada.reporting.domain.contracts.financeiro.FinanceiroResumoDTO;
import br.com.pousda.pousada.reporting.infrastructure.readrepo.financeiro.FinancialMovementReadDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static br.com.pousda.pousada.reporting.application.Timezones.BAHIA;

@Service
@RequiredArgsConstructor
public class FinanceiroReportService {

    private final FinancialMovementReadDao repo;

    public FinanceiroReportDTO gerar(FinanceiroFilter f, String geradoPor) {
        // Valida período
        ReportValidator.validar(f);

        var ini = f.getDataInicio().atStartOfDay(BAHIA).toLocalDateTime();
        var fim = f.getDataFim().plusDays(1).atStartOfDay(BAHIA).toLocalDateTime();

        // ====== trata tipo: "TODAS" -> null (sem filtro), "ENTRADA"/"SAIDA" -> mantém ======
        String filtroTipo = f.getTipo(); // vem do front: "TODAS", "ENTRADA", "SAIDA" ou null
        String tipo = (filtroTipo == null ||
                filtroTipo.trim().isEmpty() ||
                "TODAS".equalsIgnoreCase(filtroTipo))
                ? null          // null = não filtra por tipo
                : filtroTipo;   // "ENTRADA" ou "SAIDA"

        // ---------------- LINHAS DETALHADAS ----------------
        List<Object[]> rows = repo.findLinhas(ini, fim, tipo);

        List<FinanceiroLinhaDTO> linhas = rows.stream().map(r -> {
            var dto = new FinanceiroLinhaDTO();

            // [1] data_hora
            Timestamp ts = (Timestamp) r[1];
            dto.setData(ts != null ? ts.toLocalDateTime() : null);

            dto.setDescricao(Objects.toString(r[2], ""));
            dto.setValor(toBigDecimal(r[3]));          // converte Double/Number -> BigDecimal
            dto.setTipo(Objects.toString(r[4], ""));
            dto.setAutor(Objects.toString(r[5], ""));
            dto.setCodigo(Objects.toString(r[6], ""));
            return dto;
        }).collect(Collectors.toList());

        // ---------------- TOTAIS ----------------
        List<Object[]> totalsList = repo.sumTotais(ini, fim, tipo);

        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas   = BigDecimal.ZERO;

        if (!totalsList.isEmpty() && totalsList.get(0) != null) {
            Object[] row = totalsList.get(0);
            entradas = toBigDecimal(row[0]);
            saidas   = toBigDecimal(row[1]);
        }

        var resumo = new FinanceiroResumoDTO();
        resumo.setTotalEntradas(entradas);
        resumo.setTotalSaidas(saidas);
        resumo.setSaldo(entradas.subtract(saidas));

        var dto = new FinanceiroReportDTO();
        dto.setDataInicio(f.getDataInicio());
        dto.setDataFim(f.getDataFim());
        dto.setGeradoEm(LocalDateTime.now(BAHIA));
        dto.setGeradoPor(geradoPor);
        dto.setResumo(resumo);
        dto.setLinhas(linhas);

        return dto;
    }

    // --------- helper para lidar com Double / BigDecimal / Number ----------
    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        return new BigDecimal(v.toString());
    }
}
