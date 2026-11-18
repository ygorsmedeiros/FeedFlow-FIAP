package br.com.feedflow.avaliacao.api;

import br.com.feedflow.avaliacao.api.dto.ErroResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ErroInternoExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof WebApplicationException webApplicationException) {
            return webApplicationException.getResponse();
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErroResponse("ERRO_INTERNO", "Ocorreu um erro interno inesperado."))
                .build();
    }
}
