package br.com.pousda.pousada.exception;

public class SaidaNaoEncontradaException extends RuntimeException {
    public SaidaNaoEncontradaException(Long id) {
        super("Saída com ID " + id + " não encontrada.");
    }
}
