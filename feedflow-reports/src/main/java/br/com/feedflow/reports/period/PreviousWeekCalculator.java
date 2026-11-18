package br.com.feedflow.reports.period;

import br.com.feedflow.reports.domain.WeeklyPeriod;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

public final class PreviousWeekCalculator {

    public static final ZoneId REPORT_ZONE = ZoneId.of("America/Sao_Paulo");

    public WeeklyPeriod calculate(Instant reference) {
        Objects.requireNonNull(reference, "reference não pode ser nula");

        LocalDate currentWeekStart = reference.atZone(REPORT_ZONE)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate previousWeekStart = currentWeekStart.minusWeeks(1);
        return new WeeklyPeriod(previousWeekStart, currentWeekStart, REPORT_ZONE);
    }
}
