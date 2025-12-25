package br.com.pousda.pousada.dashboard.domain;

import java.time.LocalDate;
import java.util.Locale;

public enum DashboardPeriodo {
    ESTE_ANO,
    ESTE_MES,
    ULTIMOS_7_DIAS,
    HOJE;

    public LocalDate inicio(LocalDate hoje) {
        switch (this) {
            case ESTE_ANO:
                return LocalDate.of(hoje.getYear(), 1, 1);
            case ESTE_MES:
                return LocalDate.of(hoje.getYear(), hoje.getMonthValue(), 1);
            case HOJE:
                return hoje;
            case ULTIMOS_7_DIAS:
            default:
                return hoje.minusDays(6);
        }
    }

    public LocalDate fim(LocalDate hoje) {
        return hoje;
    }

    public static DashboardPeriodo from(String raw) {
        if (raw == null) return ULTIMOS_7_DIAS;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        for (DashboardPeriodo p : values()) {
            if (p.name().equals(v)) return p;
        }
        return ULTIMOS_7_DIAS;
    }
}
