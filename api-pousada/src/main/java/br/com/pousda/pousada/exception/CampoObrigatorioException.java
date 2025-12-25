package br.com.pousda.pousada.exception;


public class CampoObrigatorioException extends RuntimeException {
    public CampoObrigatorioException(String mensagem) {
        super(mensagem);
    }
}
