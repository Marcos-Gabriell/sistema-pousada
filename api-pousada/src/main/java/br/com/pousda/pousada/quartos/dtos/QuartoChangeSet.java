package br.com.pousda.pousada.quartos.dtos;

import br.com.pousda.pousada.quartos.domain.Quarto;
import br.com.pousda.pousada.quartos.domain.enuns.StatusQuarto;
import br.com.pousda.pousada.quartos.domain.enuns.TipoQuarto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuartoChangeSet {

    private boolean tipoChanged;
    private boolean valorDiariaChanged;
    private boolean capacidadeChanged;
    private boolean statusChanged;

    private String tipoFrom;
    private String tipoTo;

    private BigDecimal valorDiariaFrom;
    private BigDecimal valorDiariaTo;

    private Integer capacidadeFrom;
    private Integer capacidadeTo;

    private String statusFrom;
    private String statusTo;

    private boolean nomeChanged;
    private String nomeFrom;
    private String nomeTo;

    private boolean numeroChanged;
    private String numeroFrom;
    private String numeroTo;

    private boolean descricaoChanged;
    private String descricaoFrom;
    private String descricaoTo;

    public boolean hasChanges() {
        return tipoChanged || valorDiariaChanged || capacidadeChanged || statusChanged
                || nomeChanged || numeroChanged || descricaoChanged;
    }

    public boolean isTipoChanged() { return tipoChanged; }
    public boolean isValorDiariaChanged() { return valorDiariaChanged; }
    public boolean isCapacidadeChanged() { return capacidadeChanged; }
    public boolean isStatusChanged() { return statusChanged; }

    public static QuartoChangeSet of(Quarto before, Quarto after) {
        return diff(
                before != null ? before.getNumero() : null, after != null ? after.getNumero() : null,
                before != null ? before.getNome()   : null, after != null ? after.getNome()   : null,
                before != null ? before.getTipo()   : null, after != null ? after.getTipo()   : null,
                before != null ? before.getValorDiaria() : null, after != null ? after.getValorDiaria() : null,
                before != null ? before.getCapacidade()  : null, after != null ? after.getCapacidade()  : null,
                before != null ? before.getDescricao()   : null, after != null ? after.getDescricao()   : null,
                before != null ? before.getStatus()      : null, after != null ? after.getStatus()      : null
        );
    }

    public static QuartoChangeSet diff(
            String numeroFrom, String numeroTo,
            String nomeFrom, String nomeTo,
            TipoQuarto tipoFrom, TipoQuarto tipoTo,
            BigDecimal valorDiariaFrom, BigDecimal valorDiariaTo,
            Integer capacidadeFrom, Integer capacidadeTo,
            String descricaoFrom, String descricaoTo,
            StatusQuarto statusFrom, StatusQuarto statusTo
    ) {
        QuartoChangeSetBuilder b = QuartoChangeSet.builder();

        if (!Objects.equals(numeroFrom, numeroTo)) {
            b.numeroChanged(true).numeroFrom(numeroFrom).numeroTo(numeroTo);
        }

        if (!Objects.equals(nomeFrom, nomeTo)) {
            b.nomeChanged(true).nomeFrom(nomeFrom).nomeTo(nomeTo);
        }

        if (!Objects.equals(tipoFrom, tipoTo)) {
            b.tipoChanged(true)
                    .tipoFrom(enumToStr(tipoFrom))
                    .tipoTo(enumToStr(tipoTo));
        }

        if (!Objects.equals(valorDiariaFrom, valorDiariaTo)) {
            b.valorDiariaChanged(true)
                    .valorDiariaFrom(valorDiariaFrom)
                    .valorDiariaTo(valorDiariaTo);
        }

        if (!Objects.equals(capacidadeFrom, capacidadeTo)) {
            b.capacidadeChanged(true)
                    .capacidadeFrom(capacidadeFrom)
                    .capacidadeTo(capacidadeTo);
        }

        if (!Objects.equals(descricaoFrom, descricaoTo)) {
            b.descricaoChanged(true)
                    .descricaoFrom(descricaoFrom)
                    .descricaoTo(descricaoTo);
        }

        if (!Objects.equals(statusFrom, statusTo)) {
            b.statusChanged(true)
                    .statusFrom(enumToStr(statusFrom))
                    .statusTo(enumToStr(statusTo));
        }

        return b.build();
    }

    public String toHtml(String numeroQuarto, String autorLabel, String viaLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("Quarto ").append(escape(numeroQuarto))
                .append(" atualizado <span class='by'>por ").append(escape(autorLabel)).append("</span>");
        if (viaLabel != null && !viaLabel.isBlank()) {
            sb.append(" <span class='via'>(via ").append(escape(viaLabel)).append(")</span>");
        }
        sb.append(".");

        boolean any = false;
        StringBuilder ul = new StringBuilder("<ul class='changes'>");
        if (tipoChanged) {
            ul.append("<li><strong>tipo:</strong> <span class='from'>")
                    .append(escape(nullToDash(tipoFrom))).append("</span> <span class='arrow'>&rarr;</span> <span class='to'>")
                    .append(escape(nullToDash(tipoTo))).append("</span></li>");
            any = true;
        }
        if (valorDiariaChanged) {
            ul.append("<li><strong>valor diária:</strong> <span class='from'>")
                    .append(escape(money(valorDiariaFrom))).append("</span> <span class='arrow'>&rarr;</span> <span class='to'>")
                    .append(escape(money(valorDiariaTo))).append("</span></li>");
            any = true;
        }
        if (capacidadeChanged) {
            ul.append("<li><strong>capacidade:</strong> <span class='from'>")
                    .append(escape(intOrDash(capacidadeFrom))).append("</span> <span class='arrow'>&rarr;</span> <span class='to'>")
                    .append(escape(intOrDash(capacidadeTo))).append("</span></li>");
            any = true;
        }
        if (statusChanged) {
            ul.append("<li><strong>status:</strong> <span class='from'>")
                    .append(escape(nullToDash(statusFrom))).append("</span> <span class='arrow'>&rarr;</span> <span class='to'>")
                    .append(escape(nullToDash(statusTo))).append("</span></li>");
            any = true;
        }
        if (nomeChanged) {
            ul.append("<li><strong>nome:</strong> <span class='from'>")
                    .append(escape(nullToDash(nomeFrom))).append("</span> <span class='arrow'>&rarr;</span> <span class='to'>")
                    .append(escape(nullToDash(nomeTo))).append("</span></li>");
            any = true;
        }
        if (numeroChanged) {
            ul.append("<li><strong>número:</strong> <span class='from'>")
                    .append(escape(nullToDash(numeroFrom))).append("</span> <span class='arrow'>&rarr;</span> <span class='to'>")
                    .append(escape(nullToDash(numeroTo))).append("</span></li>");
            any = true;
        }
        if (descricaoChanged) {
            ul.append("<li><strong>descrição:</strong></li>");
            any = true;
        }
        ul.append("</ul>");

        if (any) sb.append("\n").append(ul);
        return sb.toString();
    }

    private static final Locale PT_BR = new Locale("pt", "BR");

    private static String enumToStr(Enum<?> e) { return e == null ? null : e.name(); }

    private static String money(BigDecimal v) {
        return v == null ? "—" : NumberFormat.getCurrencyInstance(PT_BR).format(v);
    }

    private static String intOrDash(Integer v) { return v == null ? "—" : String.valueOf(v); }

    private static String nullToDash(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
