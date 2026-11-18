package br.com.feedflow.avaliacao.api.dto;

import br.com.feedflow.avaliacao.domain.Avaliacao;
import br.com.feedflow.avaliacao.domain.Urgencia;

import java.time.Instant;
import java.util.UUID;

public record AvaliacaoResponse(
        UUID id,
        int nota,
        Urgencia urgencia,
        String descricao,
        Instant criadoEm,
        String mensagem) {

    private static final String MENSAGEM_COM_COMENTARIO = "Avaliação registrada com sucesso.";
    private static final String MENSAGEM_SEM_COMENTARIO = "Avaliação registrada sem comentário.";

    public static AvaliacaoResponse from(Avaliacao avaliacao) {
        String mensagem = avaliacao.descricao() == null
                ? MENSAGEM_SEM_COMENTARIO
                : MENSAGEM_COM_COMENTARIO;

        return new AvaliacaoResponse(
                avaliacao.id(),
                avaliacao.nota(),
                avaliacao.urgencia(),
                avaliacao.descricao(),
                avaliacao.criadoEm(),
                mensagem);
    }
}
