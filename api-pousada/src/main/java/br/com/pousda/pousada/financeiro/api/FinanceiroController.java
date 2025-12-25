// src/main/java/br/com/pousda/pousada/financeiro/api/FinanceiroController.java
package br.com.pousda.pousada.financeiro.api;

import br.com.pousda.pousada.financeiro.application.FinanceiroService;
import br.com.pousda.pousada.financeiro.domain.enuns.OrigemLancamento;
import br.com.pousda.pousada.financeiro.domain.enuns.TipoLancamento;
import br.com.pousda.pousada.financeiro.dtos.LancamentoDTO;
import br.com.pousda.pousada.financeiro.dtos.LancamentoResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/financeiro/lancamentos")
@RequiredArgsConstructor
public class FinanceiroController {

    private final FinanceiroService service;

    @PostMapping
    public ResponseEntity<LancamentoResponseDTO> criar(@RequestBody @Valid LancamentoDTO dto) {
        return ResponseEntity.ok(service.criarManual(dto));
    }

    @GetMapping
    public ResponseEntity<List<LancamentoResponseDTO>> listar(
            @RequestParam(required = false) TipoLancamento tipo,
            @RequestParam(required = false) OrigemLancamento origem,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        return ResponseEntity.ok(service.listar(tipo, origem, inicio, fim));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LancamentoResponseDTO> atualizar(@PathVariable Long id, @RequestBody @Valid LancamentoDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable Long id, @RequestParam(required = false) String motivo) {
        service.cancelar(id, motivo);
        return ResponseEntity.noContent().build();
    }
}
