package br.com.pousda.pousada.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            org.springframework.http.HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {

        String erro = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Erro de validação");

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Erro de validação", erro, request);
    }

    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String error, String message, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getDescription(false).substring(4));
        return new ResponseEntity<>(body, status);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(QuartoOcupadoException.class)
    public ResponseEntity<Object> handleQuartoOcupado(QuartoOcupadoException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Quarto ocupado", ex.getMessage(), request);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(QuartoNaoEncontradoException.class)
    public ResponseEntity<Object> handleQuartoNaoEncontrado(QuartoNaoEncontradoException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Quarto não encontrado", ex.getMessage(), request);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(QuartoJaLivreException.class)
    public ResponseEntity<Object> handleQuartoJaLivre(QuartoJaLivreException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Quarto já está livre", ex.getMessage(), request);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(HospedagemAtivaNaoEncontradaException.class)
    public ResponseEntity<Object> handleHospedagemAtivaNaoEncontrada(HospedagemAtivaNaoEncontradaException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Hospedagem não encontrada", ex.getMessage(), request);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(DataInvalidaException.class)
    public ResponseEntity<Object> handleDataInvalida(DataInvalidaException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Data inválida", ex.getMessage(), request);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ValorInvalidoException.class)
    public ResponseEntity<Object> handleValorInvalido(ValorInvalidoException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Valor inválido", ex.getMessage(), request);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(SaidaNaoEncontradaException.class)
    public ResponseEntity<Object> handleSaidaNaoEncontrada(SaidaNaoEncontradaException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Saída não encontrada", ex.getMessage(), request);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Dados inválidos", ex.getMessage(), request);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntime(RuntimeException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Ocorreu um erro interno no servidor.", request);
    }
}
