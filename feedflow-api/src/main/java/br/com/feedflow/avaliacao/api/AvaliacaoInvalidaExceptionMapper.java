package br.com.feedflow.avaliacao.api;

import br.com.feedflow.avaliacao.api.dto.ErroResponse;
import br.com.feedflow.avaliacao.domain.AvaliacaoInvalidaException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AvaliacaoInvalidaExceptionMapper implements ExceptionMapper<AvaliacaoInvalidaException> {

    @Override
    public Response toResponse(AvaliacaoInvalidaException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErroResponse("AVALIACAO_INVALIDA", exception.getMessage()))
                .build();
    }
}
