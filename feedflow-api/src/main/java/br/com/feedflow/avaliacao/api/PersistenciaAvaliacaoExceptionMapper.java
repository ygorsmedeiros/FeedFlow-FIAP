package br.com.feedflow.avaliacao.api;

import br.com.feedflow.avaliacao.api.dto.ErroResponse;
import br.com.feedflow.avaliacao.persistence.PersistenciaAvaliacaoException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class PersistenciaAvaliacaoExceptionMapper implements ExceptionMapper<PersistenciaAvaliacaoException> {

    @Override
    public Response toResponse(PersistenciaAvaliacaoException exception) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErroResponse(
                        "ERRO_PERSISTENCIA",
                        "Não foi possível registrar a avaliação."))
                .build();
    }
}
