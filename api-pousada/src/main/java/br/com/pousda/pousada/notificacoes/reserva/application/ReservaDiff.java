package br.com.pousda.pousada.notificacoes.reserva.application;

import br.com.pousda.pousada.reservas.domain.Reserva;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ReservaDiff {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ReservaDiff() {}

    public static String diff(Reserva before, Reserva after) {
        if (before == null || after == null) {
            return "dados atualizados";
        }

        List<String> mudancas = new ArrayList<>();

        addIfChanged(mudancas, "hóspede", before.getNome(), after.getNome());
        addIfChanged(mudancas, "telefone", before.getTelefone(), after.getTelefone());

        String qBefore = before.getQuarto() != null ? before.getQuarto().getNumero() : "-";
        String qAfter  = after.getQuarto()  != null ? after.getQuarto().getNumero()  : "-";
        addIfChanged(mudancas, "quarto", qBefore, qAfter);

        String deBefore = before.getDataEntrada() != null ? before.getDataEntrada().format(BR) : "-";
        String deAfter  = after.getDataEntrada()  != null ? after.getDataEntrada().format(BR)  : "-";
        addIfChanged(mudancas, "data de entrada", deBefore, deAfter);

        String dsBefore = before.getDataSaida() != null ? before.getDataSaida().format(BR) : "-";
        String dsAfter  = after.getDataSaida()  != null ? after.getDataSaida().format(BR)  : "-";
        addIfChanged(mudancas, "data de saída", dsBefore, dsAfter);

        addIfChanged(mudancas, "forma de pagamento", before.getFormaPagamento(), after.getFormaPagamento());

        String vtBefore = before.getValorTotal() != null ? String.format("R$ %.2f", before.getValorTotal()) : "-";
        String vtAfter  = after.getValorTotal()  != null ? String.format("R$ %.2f", after.getValorTotal())  : "-";
        addIfChanged(mudancas, "valor total", vtBefore, vtAfter);

        if (mudancas.isEmpty()) {
            return "dados atualizados";
        }
        return String.join("; ", mudancas);
    }

    private static void addIfChanged(List<String> out, String campo, String before, String after) {
        String b = blankToHifen(before);
        String a = blankToHifen(after);
        if (!b.equals(a)) {
            out.add(campo + ": " + b + " → " + a);
        }
    }

    private static String blankToHifen(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }
}
