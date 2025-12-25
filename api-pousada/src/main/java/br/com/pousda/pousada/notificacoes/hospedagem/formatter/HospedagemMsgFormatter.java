package br.com.pousda.pousada.notificacoes.hospedagem.application;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class HospedagemMsgFormatter {
    private HospedagemMsgFormatter() {}

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static String nz(String s, String d) { return (s == null || s.isBlank()) ? d : s; }

    /* ===== títulos (minúsculo) ===== */
    public static String tituloCriada()              { return "Hospedagem Criada"; }
    public static String tituloAtualizada()          { return "Hospedagem Atualizada"; }
    public static String tituloLembreteCheckout11h() { return "Atenção: Checkout Pendente"; }
    public static String tituloCheckoutAutomatico()  { return "Checkout Automático Concluído"; }

    /* ===== helpers ===== */
    public static String fmtData(String iso) {
        try { return LocalDate.parse(iso).format(BR); }
        catch (Exception e) { return nz(iso, "-"); }
    }

    public static String periodoBR(String checkinIso, String checkoutIso) {
        return fmtData(checkinIso) + " a " + fmtData(checkoutIso);
    }

    /* ================== mensagens (minúsculo) ================== */

    public static String msgCriada(Long id, String codigo, String autorNome,
                                   String quarto, String checkinIso, String checkoutIso) {
        return "Hospedagem " + nz(codigo, "-")
                + " criada por " + nz(autorNome, "usuário")
                + " | " + (id != null ? id : -1)
                + " · quarto " + nz(quarto, "-")
                + " · período " + periodoBR(checkinIso, checkoutIso);
    }

    // ex.: hospedagem abc123 atualizada por marcos | id: 10 · pagamento: CARTAO _ PIX
    public static String msgAtualizada(Long id, String codigo, String autorNome,
                                       String hospede, String campo, String de, String para) {

        // Formato especial para pagamento
        if ("pagamento".equalsIgnoreCase(campo)) {
            return "Hospedagem " + nz(codigo, "-")
                    + " atualizada por " + nz(autorNome, "usuário")
                    + " | " + (id != null ? id : -1)
                    + " - pagamento: " + nz(de, "-") + " ----> " + nz(para, "-");
        }

        // Formato normal para outros campos
        return "Hospedagem " + nz(codigo, "-")
                + " atualizada por " + nz(autorNome, "usuário")
                + " | " + (id != null ? id : -1)
                + " · " + nz(campo, "dados").toLowerCase() + ": " + nz(de, "-") + " ----> " + nz(para, "-");
    }

    public static String msgLembrete11h(Long id, String codigo, String hospede,
                                        String quarto, String dataSaidaIso) {
        return "Há hospedagem de código: " + nz(codigo, "-")
                + " do Hóspede: " + nz(hospede, "-")
                + " esta aguardando checkout. Caso não aconteça até as 12 será automático.";
    }




    public static String msgCheckoutAuto(Long id, String codigo, String hospede,
                                         String quarto, String dataSaidaIso) {
        return "Checkout automático realizado na hospedagem de código " + nz(codigo, "-")
                + " do hóspede " + nz(hospede, "-");
    }

    public static String tituloCheckoutManual() { return "Checkout Manual"; }

    public static String msgCheckoutManual(Long id, String codigo, String autorNome,
                                           String hospede, String quarto, String dataSaidaIso, String motivo) {
        return "Checkout manual realizado na hospedagem " + nz(codigo, "-")
                + " por " + nz(autorNome, "usuário")
                + " | " + (id != null ? id : -1)
                + " · hóspede: " + nz(hospede, "-")
                + " · quarto " + nz(quarto, "-")
                + " · saída " + fmtData(dataSaidaIso)
                + (motivo != null && !motivo.isBlank() ? " · motivo: " + motivo : "");
    }

    public static String targetJsonOf(Long id, String codigo, String hospede, String quarto) {
        if (id == null) return "{}";
        String c = nz(codigo, "-").replace("\"","\\\"");
        String h = nz(hospede, "-").replace("\"","\\\"");
        String q = nz(quarto, "-").replace("\"","\\\"");
        return "{\"id\":" + id + ",\"codigo\":\"" + c + "\",\"hospede\":\"" + h + "\",\"quarto\":\"" + q + "\"}";
    }
}
