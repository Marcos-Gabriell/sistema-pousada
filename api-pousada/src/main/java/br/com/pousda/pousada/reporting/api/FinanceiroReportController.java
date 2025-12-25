package br.com.pousda.pousada.reporting.api;

import br.com.pousda.pousada.reporting.application.FinanceiroReportService;
import br.com.pousda.pousada.reporting.application.PdfRendererService;
import br.com.pousda.pousada.reporting.domain.contracts.financeiro.FinanceiroFilter;
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
@RequestMapping("/api/admin/reports/financeiro")
@RequiredArgsConstructor
public class FinanceiroReportController {

    private final FinanceiroReportService service;
    private final PdfRendererService pdf;

    @PostMapping(value = "/export", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','DEV')")
    public ResponseEntity<byte[]> export(@RequestBody FinanceiroFilter f) {

        // Usuário logado
        AuthPrincipal currentUser = SecurityUtils.getCurrentAuthPrincipal();
        String userRole = currentUser.getRole();

        log.info("Tentativa de gerar RELATÓRIO FINANCEIRO por usuário: {}, Role: {}",
                currentUser.getUsername(), userRole);

        if (!"ROLE_ADMIN".equals(userRole) && !"ROLE_DEV".equals(userRole)) {
            log.warn("Acesso negado para geração de RELATÓRIO FINANCEIRO. Role do usuário: {}", userRole);
            throw new AccessDeniedException("Acesso negado para geração de relatórios");
        }

        // ====== GERADO POR: NOME | ID ======
        String username = currentUser.getUsername();
        Long id = currentUser.getId();
        String geradoPor = username + " | " + id;

        var dto = service.gerar(f, geradoPor);

        Map<String, Object> model = new HashMap<>();
        model.put("r", dto);

        byte[] bytes = pdf.render("pdf/financeiro", model);

        String fn = String.format(
                "financeiro_%s_a_%s.pdf",
                dto.getDataInicio(),
                dto.getDataFim()
        );
        String cd = "attachment; filename*=UTF-8''" +
                URLEncoder.encode(fn, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd)
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
