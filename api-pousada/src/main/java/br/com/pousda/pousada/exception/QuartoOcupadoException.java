package br.com.pousda.pousada.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class QuartoOcupadoException extends RuntimeException {

    public QuartoOcupadoException(String message) {
        super(message);
    }
}