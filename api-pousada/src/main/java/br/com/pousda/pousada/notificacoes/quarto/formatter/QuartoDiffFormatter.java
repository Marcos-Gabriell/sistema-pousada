package br.com.pousda.pousada.notificacoes.quarto.application;

import br.com.pousda.pousada.quartos.dtos.QuartoChangeSet;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuartoDiffFormatter {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private QuartoDiffFormatter() {}

    public static String format(QuartoChangeSet cs) {
        if (cs == null) return "-";

        List<String> parts = new ArrayList<>();

        // Número
        add(parts, isTrue(getBool(cs, "isNumeroChanged", "getNumeroChanged")),
                "Número", getStr(cs, "getNumeroFrom"), getStr(cs, "getNumeroTo"));

        // Nome
        add(parts, isTrue(getBool(cs, "isNomeChanged", "getNomeChanged")),
                "Nome", getStr(cs, "getNomeFrom"), getStr(cs, "getNomeTo"));

        // Tipo (enum)
        add(parts, isTrue(getBool(cs, "isTipoChanged", "getTipoChanged")),
                "Tipo", enumStr(getObj(cs, "getTipoFrom")), enumStr(getObj(cs, "getTipoTo")));

        // Valor diária (R$)
        add(parts, isTrue(getBool(cs, "isValorDiariaChanged", "getValorDiariaChanged")),
                "Valor diária", money(getObj(cs, "getValorDiariaFrom")), money(getObj(cs, "getValorDiariaTo")));

        // Capacidade
        add(parts, isTrue(getBool(cs, "isCapacidadeChanged", "getCapacidadeChanged")),
                "Capacidade", objStr(getObj(cs, "getCapacidadeFrom")), objStr(getObj(cs, "getCapacidadeTo")));

        // Descrição (curta)
        add(parts, isTrue(getBool(cs, "isDescricaoChanged", "getDescricaoChanged")),
                "Descrição", clip(getStr(cs, "getDescricaoFrom")), clip(getStr(cs, "getDescricaoTo")));

        // Status (enum)
        add(parts, isTrue(getBool(cs, "isStatusChanged", "getStatusChanged")),
                "Status", enumStr(getObj(cs, "getStatusFrom")), enumStr(getObj(cs, "getStatusTo")));

        if (parts.isEmpty()) return "Sem alterações relevantes";
        return String.join(" • ", parts);
    }

    /* ---------------- helpers ---------------- */

    private static void add(List<String> out, boolean cond, String label, String from, String to) {
        if (!cond) return;
        out.add(label + ": " + nv(from) + " → " + nv(to));
    }

    private static boolean isTrue(Boolean b) { return b != null && b; }

    private static String money(Object v) {
        if (v == null) return "-";
        if (v instanceof Number) return BRL.format(((Number) v).doubleValue());
        try { return BRL.format(Double.parseDouble(v.toString())); } catch (Exception e) { return v.toString(); }
    }

    private static String clip(String s) {
        if (s == null) return "-";
        String t = s.trim();
        if (t.length() <= 60) return t;
        return t.substring(0, 57) + "...";
    }

    private static String nv(String s) { return (s == null || s.isBlank()) ? "-" : s; }

    private static String objStr(Object o) { return o == null ? "-" : String.valueOf(o); }

    private static String enumStr(Object o) { return objStr(o); }

    /* ---- reflection-safe getters (aceita diferentes nomes) ---- */
    private static Boolean getBool(Object o, String... methods) {
        for (String m : methods) {
            try { return (Boolean) o.getClass().getMethod(m).invoke(o); } catch (Exception ignored) {}
        }
        return false;
    }

    private static String getStr(Object o, String method) {
        try {
            Object val = o.getClass().getMethod(method).invoke(o);
            return val == null ? null : String.valueOf(val);
        } catch (Exception e) { return null; }
    }

    private static Object getObj(Object o, String method) {
        try { return o.getClass().getMethod(method).invoke(o); } catch (Exception e) { return null; }
    }
}
