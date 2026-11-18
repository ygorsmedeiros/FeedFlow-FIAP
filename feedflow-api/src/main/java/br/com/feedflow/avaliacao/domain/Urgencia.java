package br.com.feedflow.avaliacao.domain;

public enum Urgencia {
    CRITICA,
    MEDIA,
    BAIXA;

    public static Urgencia classificar(int nota) {
        if (nota < 0 || nota > 10) {
            throw new AvaliacaoInvalidaException("A nota deve ser um número inteiro entre 0 e 10.");
        }

        if (nota <= 3) {
            return CRITICA;
        }
        if (nota <= 6) {
            return MEDIA;
        }
        return BAIXA;
    }
}
