package br.com.feedflow.notifications.domain;

import java.time.Instant;
import java.util.UUID;

public record CriticalFeedback(
        UUID id,
        int nota,
        String urgencia,
        String descricao,
        Instant criadoEm) {
}
