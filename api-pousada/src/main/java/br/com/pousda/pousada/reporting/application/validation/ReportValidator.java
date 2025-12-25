package br.com.pousda.pousada.reporting.application.validation;




import br.com.pousda.pousada.reporting.domain.contracts.common.PeriodoFilter;

import java.time.temporal.ChronoUnit;

public final class ReportValidator {
    private ReportValidator() {}

    public static void validar(PeriodoFilter p) {
        if (p == null || p.getDataInicio() == null || p.getDataFim() == null)
            throw new IllegalArgumentException("Período obrigatório (dataInicio e dataFim).");
        if (p.getDataInicio().isAfter(p.getDataFim()))
            throw new IllegalArgumentException("dataInicio não pode ser maior que dataFim.");
        long dias = ChronoUnit.DAYS.between(p.getDataInicio(), p.getDataFim());
        if (dias > 366) throw new IllegalArgumentException("Intervalo máximo é 366 dias.");
    }
}
