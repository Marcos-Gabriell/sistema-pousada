package br.com.pousda.pousada.exception;

public class QuartoNaoEncontradoException extends RuntimeException {
    public QuartoNaoEncontradoException(String numero) {
        super("Quarto número " + numero + " não foi encontrado.");
    }
}