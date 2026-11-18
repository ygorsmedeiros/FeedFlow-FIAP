package br.com.feedflow.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @GET
    public HealthResponse health() {
        return new HealthResponse("UP", "FeedFlow");
    }

    public record HealthResponse(String status, String application) {
    }
}
