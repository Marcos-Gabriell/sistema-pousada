package br.com.pousda.pousada.util;


import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class Formats {
    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
    private Formats() {}
    public static String money(BigDecimal v) {
        if (v == null) return "—";
        return BRL.format(v);
    }
}
