package br.com.feedflow.reports.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record WeeklyPeriod(
        LocalDate startDate,
        LocalDate endDateExclusive,
        ZoneId zone) {

    public WeeklyPeriod {
        Objects.requireNonNull(startDate, "startDate não pode ser nula");
        Objects.requireNonNull(endDateExclusive, "endDateExclusive não pode ser nula");
        Objects.requireNonNull(zone, "zone não pode ser nula");
        if (ChronoUnit.DAYS.between(startDate, endDateExclusive) != 7) {
            throw new IllegalArgumentException("O período semanal deve possuir exatamente sete dias");
        }
    }

    public String weekKey() {
        return "SEMANA#" + startDate;
    }

    public Instant startInclusive() {
        return startDate.atStartOfDay(zone).toInstant();
    }

    public Instant endExclusive() {
        return endDateExclusive.atStartOfDay(zone).toInstant();
    }
}
