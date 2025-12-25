package br.com.pousda.pousada.reporting.application;

import br.com.pousda.pousada.reporting.application.validation.ReportValidator;
import br.com.pousda.pousada.reporting.domain.contracts.geral.GeralReportDTO;
import br.com.pousda.pousada.reporting.infrastructure.readrepo.financeiro.FinancialMovementReadDao;
import br.com.pousda.pousada.reporting.infrastructure.readrepo.hospedagens.HospedagensReadDao;
import br.com.pousda.pousada.reporting.infrastructure.readrepo.quartos.QuartosReadDao;
import br.com.pousda.pousada.reporting.infrastructure.readrepo.reservas.ReservasReadDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static br.com.pousda.pousada.reporting.application.Timezones.BAHIA;

@Service
@RequiredArgsConstructor
public class GeralReportService {

    private final FinancialMovementReadDao finRepo;
    private final HospedagensReadDao hospRepo;
    private final QuartosReadDao quartosRepo;
    private final ReservasReadDao reservasRepo;

    public GeralReportDTO gerar(LocalDate ini, LocalDate fim, String geradoPor) {

        ReportValidator.validar(new br.com.pousda.pousada.reporting.domain.contracts.common.PeriodoFilter() {{
            setDataInicio(ini);
            setDataFim(fim);
        }});

        LocalDateTime iniDT = ini.atStartOfDay(BAHIA).toLocalDateTime();
        LocalDateTime fimDT = fim.plusDays(1).atStartOfDay(BAHIA).toLocalDateTime();

        // === FINANCEIRO: TOTAIS ===
        List<Object[]> totalsList = finRepo.sumTotais(iniDT, fimDT, null);

        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas   = BigDecimal.ZERO;

        if (!totalsList.isEmpty() && totalsList.get(0) != null) {
            Object[] row = totalsList.get(0);
            entradas = toBigDecimal(row[0]);
            saidas   = toBigDecimal(row[1]);
        }

        BigDecimal saldo = entradas.subtract(saidas);

        // === OUTROS KPIs ===
        long reservasPendentes = reservasRepo.countPendentes(ini, fim);

        // ✅ HOSPEDAGENS ATIVAS (status atual)
        long hospedagensAtivas = hospRepo.countAtivas();

        // ✅ QUARTOS POR STATUS (status atual)
        // ⚠️ Ajuste os textos conforme seu banco/enum
        long quartosDisponiveis = quartosRepo.countByStatus("DISPONIVEL");
        long quartosOcupados    = quartosRepo.countByStatus("OCUPADO");
        long quartosManutencao  = quartosRepo.countByStatus("MANUTENCAO");

        // (se quiser usar ocupação média depois, você já tem)
        // Double ocupacao = quartosRepo.ocupacaoMedia(ini, fim);

        // === MINI-LISTA ÚLTIMAS MOVIMENTAÇÕES (10) ===
        List<Map<String, Object>> ultimas = new ArrayList<>();
        List<Object[]> rows = finRepo.findLinhas(iniDT, fimDT, null);
        int limit = Math.min(10, rows.size());

        for (int i = 0; i < limit; i++) {
            Object[] r = rows.get(i);
            Map<String, Object> item = new HashMap<>();

            Timestamp ts = (Timestamp) r[1];
            item.put("data", ts != null ? ts.toLocalDateTime() : null);

            item.put("descricao", r[2]);
            item.put("valor", toBigDecimal(r[3]));
            item.put("tipo", r[4]);
            item.put("autor", r[5]);
            item.put("codigo", r[6]);

            ultimas.add(item);
        }

        // === DTO ===
        GeralReportDTO out = new GeralReportDTO();
        out.setDataInicio(ini);
        out.setDataFim(fim);
        out.setGeradoEm(LocalDateTime.now(BAHIA));
        out.setGeradoPor(geradoPor);

        out.setReservasPendentes(reservasPendentes);

        out.setHospedagensAtivas(hospedagensAtivas);
        out.setQuartosDisponiveis(quartosDisponiveis);
        out.setQuartosOcupados(quartosOcupados);
        out.setQuartosManutencao(quartosManutencao);

        out.setEntradas(entradas);
        out.setSaidas(saidas);
        out.setSaldo(saldo);

        out.setUltimasMovimentacoes(ultimas);

        return out;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        return new BigDecimal(v.toString());
    }
}
