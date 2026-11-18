package br.com.feedflow.avaliacao.api;

import br.com.feedflow.avaliacao.api.dto.ErroResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ErroInternoExceptionMapperTest {

    @Test
    void deveOcultarDetalhesDeErroInterno() {
        ErroInternoExceptionMapper mapper = new ErroInternoExceptionMapper();

        Response response = mapper.toResponse(new IllegalStateException("detalhe técnico sensível"));
        ErroResponse erro = (ErroResponse) response.getEntity();

        assertEquals(500, response.getStatus());
        assertEquals("ERRO_INTERNO", erro.codigo());
        assertEquals("Ocorreu um erro interno inesperado.", erro.mensagem());
        assertFalse(erro.mensagem().contains("detalhe técnico sensível"));
    }
}
