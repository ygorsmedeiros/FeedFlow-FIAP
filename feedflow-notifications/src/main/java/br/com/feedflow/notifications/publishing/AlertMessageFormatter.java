package br.com.feedflow.notifications.publishing;

import br.com.feedflow.notifications.domain.CriticalFeedback;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class AlertMessageFormatter {

    private final Clock clock;

    public AlertMessageFormatter() {
        this(Clock.systemUTC());
    }

    public AlertMessageFormatter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock não pode ser nulo");
    }

    public String format(CriticalFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback não pode ser nulo");
        Instant generatedAt = clock.instant();
        return """
                FeedFlow - alerta de avaliação crítica

                ID da avaliação: %s
                Nota: %d
                Urgência: %s
                Descrição: %s
                Criada em (UTC): %s
                Alerta gerado em (UTC): %s
                """.formatted(
                feedback.id(),
                feedback.nota(),
                feedback.urgencia(),
                feedback.descricao(),
                feedback.criadoEm(),
                generatedAt);
    }
}
