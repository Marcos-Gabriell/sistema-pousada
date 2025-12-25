package br.com.pousda.pousada.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

public final class CodigoHospedagemGenerator {
    private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");
    private CodigoHospedagemGenerator() {}

    public static String gerar() {
        String ano = String.valueOf(LocalDate.now(ZONE_BR).getYear());
        String sufixo = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        return ano + sufixo;
    }
}
