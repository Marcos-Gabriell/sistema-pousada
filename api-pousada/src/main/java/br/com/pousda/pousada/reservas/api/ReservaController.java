// src/main/java/br/com/pousda/pousada/reservas/interfaces/ReservaController.java
package br.com.pousda.pousada.reservas.interfaces;

import br.com.pousda.pousada.reservas.application.ReservaService;
import br.com.pousda.pousada.reservas.dtos.ReservaDTO;
import br.com.pousda.pousada.reservas.dtos.ReservaResponseDTO;
import br.com.pousda.pousada.reservas.dtos.ConfirmarReservaDTO;
import br.com.pousda.pousada.quartos.domain.Quarto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    @GetMapping
    public ResponseEntity<List<ReservaResponseDTO>> listarReservas() {
        return ResponseEntity.ok(reservaService.listarReservas());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ReservaResponseDTO>> listarPorStatus(@PathVariable String status) {
        return ResponseEntity.ok(reservaService.listarReservasPorStatus(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponseDTO> buscarPorId(@PathVariable Long id) {
        Optional<ReservaResponseDTO> reserva = reservaService.buscarPorId(id);
        return reserva.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ReservaResponseDTO> criarReserva(@Valid @RequestBody ReservaDTO dto) {
        return ResponseEntity.ok(reservaService.criarReserva(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReservaResponseDTO> editarReserva(
            @PathVariable Long id,
            @Valid @RequestBody ReservaDTO dto
    ) {
        return ResponseEntity.ok(reservaService.editarReserva(id, dto));
    }

    // ✅ cancelamento só com motivo (string simples)
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarReserva(
            @PathVariable Long id,
            @RequestBody(required = false) String motivo
    ) {
        reservaService.cancelarReserva(id, motivo);
        return ResponseEntity.ok().build();
    }

    // ✅ confirmação sem CPF/email obrigatórios
    @PutMapping("/{id}/confirmar")
    public ResponseEntity<ReservaResponseDTO> confirmarReserva(
            @PathVariable Long id,
            @RequestBody(required = false) ConfirmarReservaDTO dto
    ) {
        return ResponseEntity.ok(reservaService.confirmarReserva(id, dto));
    }

    @GetMapping("/quartos/disponiveis")
    public ResponseEntity<List<Quarto>> listarQuartosDisponiveis(
            @RequestParam("dataEntrada")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataEntrada,
            @RequestParam("dataSaida")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataSaida
    ) {
        return ResponseEntity.ok(
                reservaService.listarQuartosDisponiveis(dataEntrada, dataSaida)
        );
    }
}
