package br.com.pousda.pousada.reporting.application;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PdfRendererService {

    private final TemplateEngine templateEngine;

    public byte[] render(String template, Map<String, Object> model) {
        Context ctx = new Context();
        ctx.setVariables(model);

        String html = templateEngine.process(template, ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ✅ Base URI: aponta para /static/
            // HTML deve referenciar assim: src="images/logo-pousada.png"
            String baseUri = new ClassPathResource("static/")
                    .getURL()
                    .toExternalForm();

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, baseUri);
            builder.toStream(out);
            builder.run();

            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Falha ao gerar PDF: " + e.getMessage(), e);
        }
    }
}
