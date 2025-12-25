package br.com.pousda.pousada.dashboard.api;

import br.com.pousda.pousada.dashboard.application.DashboardService;
import br.com.pousda.pousada.dashboard.domain.DashboardPeriodo;
import br.com.pousda.pousada.dashboard.dto.DashboardResumoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/resumo")
    @PreAuthorize("hasAnyRole('ADMIN','DEV')")
    public DashboardResumoDTO getResumo(
            @RequestParam(defaultValue = "ULTIMOS_7_DIAS") DashboardPeriodo periodo
    ) {
        return dashboardService.getResumo(periodo);
    }
}
