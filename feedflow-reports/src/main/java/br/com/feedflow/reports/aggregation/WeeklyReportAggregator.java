package br.com.feedflow.reports.aggregation;

import br.com.feedflow.reports.domain.Feedback;
import br.com.feedflow.reports.domain.Urgency;
import br.com.feedflow.reports.domain.WeeklyPeriod;
import br.com.feedflow.reports.domain.WeeklyReport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WeeklyReportAggregator {

    public WeeklyReport aggregate(WeeklyPeriod period, List<Feedback> feedbacks) {
        Objects.requireNonNull(period, "period não pode ser nulo");
        Objects.requireNonNull(feedbacks, "feedbacks não pode ser nulo");

        List<Feedback> sortedFeedbacks = new ArrayList<>(feedbacks);
        sortedFeedbacks.sort(Comparator.comparing(Feedback::createdAt));

        Map<Urgency, Long> countByUrgency = zeroedUrgencies();
        Map<DayOfWeek, Long> countByDay = zeroedDays();
        long scoreSum = 0;

        for (Feedback feedback : sortedFeedbacks) {
            countByUrgency.compute(feedback.urgency(), (urgency, count) -> count + 1);
            DayOfWeek localDay = feedback.createdAt().atZone(period.zone()).getDayOfWeek();
            countByDay.compute(localDay, (day, count) -> count + 1);
            scoreSum += feedback.score();
        }

        Optional<BigDecimal> average = sortedFeedbacks.isEmpty()
                ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(scoreSum)
                        .divide(BigDecimal.valueOf(sortedFeedbacks.size()), 2, RoundingMode.HALF_UP));

        return new WeeklyReport(
                period,
                sortedFeedbacks,
                sortedFeedbacks.size(),
                average,
                countByUrgency,
                countByDay);
    }

    private Map<Urgency, Long> zeroedUrgencies() {
        Map<Urgency, Long> counts = new EnumMap<>(Urgency.class);
        for (Urgency urgency : Urgency.values()) {
            counts.put(urgency, 0L);
        }
        return counts;
    }

    private Map<DayOfWeek, Long> zeroedDays() {
        Map<DayOfWeek, Long> counts = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            counts.put(day, 0L);
        }
        return counts;
    }
}
