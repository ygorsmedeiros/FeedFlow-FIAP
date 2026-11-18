package br.com.feedflow.reports.period;

import br.com.feedflow.reports.domain.WeeklyPeriod;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreviousWeekCalculatorTest {

    private final PreviousWeekCalculator calculator = new PreviousWeekCalculator();

    @Test
    void deveCalcularSemanaAnteriorCompleta() {
        WeeklyPeriod period = calculator.calculate(Instant.parse("2026-11-12T15:00:00Z"));

        assertEquals(LocalDate.parse("2026-11-02"), period.startDate());
        assertEquals(LocalDate.parse("2026-11-09"), period.endDateExclusive());
        assertEquals("SEMANA#2026-11-02", period.weekKey());
        assertEquals(Instant.parse("2026-11-02T03:00:00Z"), period.startInclusive());
        assertEquals(Instant.parse("2026-11-09T03:00:00Z"), period.endExclusive());
    }

    @Test
    void deveCalcularSemanaAnteriorNaTransicaoDeAno() {
        WeeklyPeriod period = calculator.calculate(Instant.parse("2026-01-05T12:00:00Z"));

        assertEquals(LocalDate.parse("2025-12-29"), period.startDate());
        assertEquals(LocalDate.parse("2026-01-05"), period.endDateExclusive());
        assertEquals("SEMANA#2025-12-29", period.weekKey());
    }
}
