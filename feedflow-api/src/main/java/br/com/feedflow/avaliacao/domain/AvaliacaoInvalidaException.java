package br.com.feedflow.avaliacao.domain;

public class AvaliacaoInvalidaException extends RuntimeException {

    public AvaliacaoInvalidaException(String message) {
        super(message);
    }
}
