package br.com.pousda.pousada.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;

@RestControllerAdvice
public class ExceptionHandlerAdvice {

    @ExceptionHandler({CampoObrigatorioException.class, ValorInvalidoException.class, QuartoOcupadoException.class, QuartoNaoEncontradoException.class, DataInvalidaException.class})
    public ResponseEntity<?> handleCustomExceptions(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleOtherExceptions(Exception ex) {
        return ResponseEntity.status(500).body(Collections.singletonMap("error", "Erro inesperado: " + ex.getMessage()));
    }
}
