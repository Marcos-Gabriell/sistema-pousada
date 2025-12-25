package br.com.pousda.pousada.notificacoes;


import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ResumoParserService {

    public static final class Change {
        public final String campo, de, para;
        public Change(String c, String d, String p) {
            this.campo = c;
            this.de = d;
            this.para = p;
        }
    }

    /* ===== Parser específico para pagamento ===== */
    public Change parseResumoPagamento(String resumo) {
        String r = (resumo == null) ? "" : resumo.trim();
        Pattern pagamentoPattern = Pattern.compile(
                "(?i)^\\s*pagamento\\s*:\\s*(.*?)\\s*(?:---->|->|→)\\s*(.*?)\\s*$"
        );
        java.util.regex.Matcher m = pagamentoPattern.matcher(r);
        if (m.matches()) {
            String de = clean(m.group(1));
            String para = clean(m.group(2));
            return new Change("pagamento", de, para);
        }
        return parseResumoDePara(resumo);
    }

    /* ===== Helpers para extrair "campo: DE -> PARA" do resumo ===== */
    private static final Pattern SETA_PATTERN = Pattern.compile(
            "(?i)^\\s*([\\p{L}][\\p{L} ]*?)\\s*:\\s*(.*?)\\s*(?:---->|->|→)\\s*(.*?)\\s*$"
    );

    private static final Pattern NOVO_VALOR_PATTERN = Pattern.compile(
            "(?i)^\\s*([\\p{L}][\\p{L} ]*?)\\s*:\\s*(.*?)\\s*$"
    );

    private static String clean(String s) {
        if (s == null) return "-";
        s = s.trim();
        s = s.replaceAll("\\s*[\\.]$", "");
        return s.isEmpty() ? "-" : s;
    }

    public Change parseResumoDePara(String resumo) {
        String r = (resumo == null) ? "" : resumo.trim();
        java.util.regex.Matcher m = SETA_PATTERN.matcher(r);
        if (m.matches()) {
            String campo = clean(m.group(1)).toLowerCase();
            String de    = clean(m.group(2));
            String para  = clean(m.group(3));
            return new Change(campo, de, para);
        }
        m = NOVO_VALOR_PATTERN.matcher(r);
        if (m.matches()) {
            String campo = clean(m.group(1)).toLowerCase();
            String para  = clean(m.group(2));
            return new Change(campo, "-", para);
        }
        return new Change("dados", "-", clean(r));
    }
}