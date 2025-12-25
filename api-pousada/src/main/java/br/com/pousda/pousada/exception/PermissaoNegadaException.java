package br.com.pousda.pousada.exception;


import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class PermissaoNegadaException extends RuntimeException {
    public PermissaoNegadaException(String msg) { super(msg); }
}
