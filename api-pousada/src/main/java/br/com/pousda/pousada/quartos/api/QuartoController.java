package br.com.pousda.pousada.quartos.api;

import br.com.pousda.pousada.quartos.domain.Quarto;
import br.com.pousda.pousada.quartos.domain.enuns.StatusQuarto;
import br.com.pousda.pousada.quartos.domain.enuns.TipoQuarto;
import br.com.pousda.pousada.quartos.application.QuartoService;
import br.com.pousda.pousada.reservas.application.ReservaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/quartos")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class QuartoController {

    private final QuartoService quartoService;
    private final ReservaService reservaService;

    @GetMapping
    public ResponseEntity<List<Quarto>> listarTodos() {
        try {
            return ResponseEntity.ok(quartoService.listarTodos());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Quarto>> listarPorStatus(@PathVariable String status) {
        try {
            StatusQuarto statusQuarto = StatusQuarto.valueOf(status.toUpperCase());
            return ResponseEntity.ok(quartoService.listarPorStatus(statusQuarto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Quarto> buscarPorId(@PathVariable Long id) {
        try {
            return quartoService.buscarPorId(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<Quarto>> buscar(
            @RequestParam(required = false) StatusQuarto status,
            @RequestParam(required = false) TipoQuarto tipo,
            @RequestParam(required = false) String termo) {
        try {
            return ResponseEntity.ok(quartoService.filtrar(status, tipo, termo));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<?> criar(@RequestBody @Valid Quarto quarto, HttpServletRequest req) {
        try {
            Quarto criado = quartoService.criar(quarto);
            URI location = URI.create(req.getRequestURI() + "/" + criado.getId());
            return ResponseEntity.created(location).body(criado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@PathVariable Long id, @RequestBody @Valid Quarto quarto) {
        try {
            return ResponseEntity.ok(quartoService.editar(id, quarto));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno");
        }
    }

    @PutMapping("/{id}/manutencao")
    public ResponseEntity<?> colocarEmManutencao(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(quartoService.colocarEmManutencao(id));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno");
        }
    }

    @PutMapping("/{id}/liberar")
    public ResponseEntity<?> liberarManutencao(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(quartoService.liberarManutencao(id));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluir(@PathVariable Long id) {
        try {
            quartoService.excluir(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno");
        }
    }

    @GetMapping("/disponiveis")
    public ResponseEntity<?> listarQuartosDisponiveis(
            @RequestParam("dataEntrada") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataEntrada,
            @RequestParam("dataSaida") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataSaida) {
        try {
            if (dataEntrada == null || dataSaida == null || !dataSaida.isAfter(dataEntrada)) {
                return ResponseEntity.badRequest().body("Data de saída deve ser posterior à data de entrada.");
            }
            return ResponseEntity.ok(reservaService.listarQuartosDisponiveis(dataEntrada, dataSaida));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno");
        }
    }

    @GetMapping("/count")
    public ResponseEntity<Long> contarQuartos() {
        try {
            return ResponseEntity.ok(quartoService.contarTodos());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/count/{status}")
    public ResponseEntity<Long> contarPorStatus(@PathVariable String status) {
        try {
            StatusQuarto statusQuarto = StatusQuarto.valueOf(status.toUpperCase());
            return ResponseEntity.ok(quartoService.contarPorStatus(statusQuarto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}