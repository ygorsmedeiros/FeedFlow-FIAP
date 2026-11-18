package br.com.feedflow.avaliacao.api.dto;

import br.com.feedflow.avaliacao.domain.Avaliacao;
import br.com.feedflow.avaliacao.domain.Urgencia;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AvaliacaoResponseTest {

    @Test
    void devePrepararContratoDeSucessoSemInventarDescricao() {
        Avaliacao avaliacao = new Avaliacao(
                UUID.randomUUID(),
                6,
                Urgencia.MEDIA,
                null,
                Instant.parse("2026-11-01T12:00:00Z"));

        AvaliacaoResponse response = AvaliacaoResponse.from(avaliacao);

        assertNull(response.descricao());
        assertEquals("Avaliação registrada sem comentário.", response.mensagem());
    }
}
