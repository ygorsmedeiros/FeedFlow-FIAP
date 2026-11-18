package br.com.feedflow.avaliacao.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Avaliacao(
        UUID id,
        int nota,
        Urgencia urgencia,
        String descricao,
        Instant criadoEm) {

    public Avaliacao {
        Objects.requireNonNull(id, "id não pode ser nulo");
        Objects.requireNonNull(urgencia, "urgencia não pode ser nula");
        Objects.requireNonNull(criadoEm, "criadoEm não pode ser nulo");
    }
}
