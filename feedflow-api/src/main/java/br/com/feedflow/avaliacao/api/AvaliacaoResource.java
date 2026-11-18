package br.com.feedflow.avaliacao.api;

import br.com.feedflow.avaliacao.api.dto.AvaliacaoRequest;
import br.com.feedflow.avaliacao.api.dto.AvaliacaoResponse;
import br.com.feedflow.avaliacao.application.AvaliacaoService;
import br.com.feedflow.avaliacao.domain.Avaliacao;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AvaliacaoResource {

    @Inject
    AvaliacaoRequestMapper requestMapper;

    @Inject
    AvaliacaoService avaliacaoService;

    @POST
    @Path("avaliacao")
    public Response registrar(AvaliacaoRequest request) {
        Avaliacao avaliacaoPersistida = avaliacaoService.registrar(requestMapper.mapear(request));
        return Response.status(Response.Status.CREATED)
                .entity(AvaliacaoResponse.from(avaliacaoPersistida))
                .build();
    }
}
