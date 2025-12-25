package br.com.pousda.pousada.reporting.api;

import br.com.pousda.pousada.reporting.application.GeralReportService;
import br.com.pousda.pousada.reporting.application.PdfRendererService;
import br.com.pousda.pousada.reporting.domain.contracts.common.PeriodoFilter;
import br.com.pousda.pousada.security.AuthPrincipal;
import br.com.pousda.pousada.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/reports/geral")
@RequiredArgsConstructor
public class GeralReportController {

    private final GeralReportService service;
    private final PdfRendererService pdf;

    @PostMapping(value = "/export", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','DEV')")
    public ResponseEntity<byte[]> export(@RequestBody PeriodoFilter f) {

        AuthPrincipal currentUser = SecurityUtils.getCurrentAuthPrincipal();
        String userRole = currentUser.getRole();

        if (!"ROLE_ADMIN".equals(userRole) && !"ROLE_DEV".equals(userRole)) {
            throw new AccessDeniedException("Acesso negado para geração de relatórios");
        }

        String geradoPor = currentUser.getUsername() + " | " + currentUser.getId();

        var dto = service.gerar(f.getDataInicio(), f.getDataFim(), geradoPor);

        Map<String, Object> model = new HashMap<>();
        model.put("r", dto);

        // ✅ IMPORTANTE: sem isso, o fragment quebra
        model.put("pousadaNome", "Pousada do Brejo");
        model.put("pousadaSubtitulo", "Sistema de Gestão • Relatórios");

        byte[] bytes = pdf.render("pdf/geral", model);

        String fn = String.format("geral_%s_a_%s.pdf", dto.getDataInicio(), dto.getDataFim());
        String cd = "attachment; filename*=UTF-8''" + URLEncoder.encode(fn, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd)
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
