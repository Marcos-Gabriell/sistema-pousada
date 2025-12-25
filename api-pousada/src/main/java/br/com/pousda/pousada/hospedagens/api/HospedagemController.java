package br.com.pousda.pousada.hospedagens.api;

import br.com.pousda.pousada.hospedagens.application.HospedagemService;
import br.com.pousda.pousada.hospedagens.domain.Hospedagem;
import br.com.pousda.pousada.hospedagens.domain.enuns.TipoHospedagem;
import br.com.pousda.pousada.hospedagens.dtos.CheckoutDTO;
import br.com.pousda.pousada.hospedagens.dtos.HospedagemDTO;
import br.com.pousda.pousada.hospedagens.dtos.HospedagemResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/hospedagens")
public class HospedagemController {

    @Autowired private HospedagemService service;

    /* ======== usado ao abrir o modal de edição ======== */
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.buscarPorIdDTO(id));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("mensagem", e.getMessage()));
        }
    }

    /* ======== editar ======== */
    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@PathVariable Long id, @RequestBody HospedagemDTO dto) {
        try {
            Hospedagem h = service.editarHospedagem(id, dto);
            return ResponseEntity.ok(service.toResponseDTO(h));
        } catch (Exception e) {
            HttpStatus status =
                    (e instanceof br.com.pousda.pousada.exception.HospedagemNaoEncontradaException) ? HttpStatus.NOT_FOUND :
                            (e instanceof br.com.pousda.pousada.exception.OperacaoNaoPermitidaException ||
                                    e instanceof br.com.pousda.pousada.exception.QuartoNaoEncontradoException ||
                                    e instanceof br.com.pousda.pousada.exception.QuartoOcupadoException) ? HttpStatus.BAD_REQUEST :
                                    HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status).body(Collections.singletonMap("mensagem", e.getMessage()));
        }
    }

    /* ======== checkin ======== */
    @PostMapping("/checkin")
    public ResponseEntity<?> checkin(@RequestBody HospedagemDTO dto) {
        try {
            Hospedagem h = service.realizarCheckin(dto);
            return ResponseEntity.ok(service.toResponseDTO(h));
        } catch (Exception e) {
            HttpStatus status =
                    (e instanceof br.com.pousda.pousada.exception.CampoObrigatorioException ||
                            e instanceof br.com.pousda.pousada.exception.ValorInvalidoException ||
                            e instanceof br.com.pousda.pousada.exception.QuartoOcupadoException ||
                            e instanceof br.com.pousda.pousada.exception.QuartoNaoEncontradoException)
                            ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status).body(Collections.singletonMap("mensagem", e.getMessage()));
        }
    }

    /* ======== checkout manual ======== */
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody CheckoutDTO dto) {
        try {
            Hospedagem h = service.realizarCheckoutPorNumero(dto);
            return ResponseEntity.ok(service.toResponseDTO(h));
        } catch (Exception e) {
            HttpStatus status =
                    (e instanceof br.com.pousda.pousada.exception.CampoObrigatorioException ||
                            e instanceof br.com.pousda.pousada.exception.QuartoJaLivreException ||
                            e instanceof br.com.pousda.pousada.exception.QuartoNaoEncontradoException)
                            ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status).body(Collections.singletonMap("mensagem", e.getMessage()));
        }
    }

    /* ======== delete (somente ADMIN ou DEV) ======== */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        try {
            service.deletarHospedagem(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            HttpStatus status =
                    (e instanceof br.com.pousda.pousada.exception.HospedagemNaoEncontradaException) ? HttpStatus.NOT_FOUND :
                            (e instanceof br.com.pousda.pousada.exception.OperacaoNaoPermitidaException) ? HttpStatus.FORBIDDEN :
                                    HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status).body(Collections.singletonMap("mensagem", e.getMessage()));
        }
    }

    /* ======== listagem para a grade ======== */
    @GetMapping
    public ResponseEntity<List<HospedagemResponseDTO>> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) TipoHospedagem tipo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status
    ) {
        List<HospedagemResponseDTO> lista = service
                .listar(nome, tipo, dataInicio, dataFim, status)
                .stream()
                .map(service::toResponseDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(lista);
    }
}
