package br.com.feedflow.reports.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record WeeklyReport(
        WeeklyPeriod period,
        List<Feedback> feedbacks,
        int total,
        Optional<BigDecimal> averageScore,
        Map<Urgency, Long> countByUrgency,
        Map<DayOfWeek, Long> countByDay) {

    public WeeklyReport {
        Objects.requireNonNull(period, "period não pode ser nulo");
        feedbacks = List.copyOf(Objects.requireNonNull(feedbacks, "feedbacks não pode ser nulo"));
        Objects.requireNonNull(averageScore, "averageScore não pode ser nula");
        countByUrgency = Map.copyOf(Objects.requireNonNull(
                countByUrgency,
                "countByUrgency não pode ser nulo"));
        countByDay = Map.copyOf(Objects.requireNonNull(countByDay, "countByDay não pode ser nulo"));
        if (total != feedbacks.size()) {
            throw new IllegalArgumentException("total deve corresponder à quantidade de avaliações");
        }
    }
}
