package br.com.feedflow.reports.domain;

import java.time.Instant;
import java.util.Objects;

public record Feedback(
        String id,
        int score,
        Urgency urgency,
        String description,
        Instant createdAt) {

    public Feedback {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id não pode estar vazio");
        }
        if (score < 0 || score > 10) {
            throw new IllegalArgumentException("score deve estar entre 0 e 10");
        }
        Objects.requireNonNull(urgency, "urgency não pode ser nula");
        Objects.requireNonNull(createdAt, "createdAt não pode ser nulo");
    }
}
