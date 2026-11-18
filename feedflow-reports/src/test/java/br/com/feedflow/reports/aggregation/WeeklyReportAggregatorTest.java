package br.com.feedflow.reports.aggregation;

import br.com.feedflow.reports.domain.Feedback;
import br.com.feedflow.reports.domain.Urgency;
import br.com.feedflow.reports.domain.WeeklyPeriod;
import br.com.feedflow.reports.domain.WeeklyReport;
import br.com.feedflow.reports.period.PreviousWeekCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyReportAggregatorTest {

    private final WeeklyReportAggregator aggregator = new WeeklyReportAggregator();
    private final WeeklyPeriod period = new PreviousWeekCalculator()
            .calculate(Instant.parse("2026-11-12T15:00:00Z"));

    @Test
    void deveGerarMetricasZeradasQuandoNaoExistiremAvaliacoes() {
        WeeklyReport report = aggregator.aggregate(period, List.of());

        assertEquals(0, report.total());
        assertTrue(report.averageScore().isEmpty());
        for (Urgency urgency : Urgency.values()) {
            assertEquals(0L, report.countByUrgency().get(urgency));
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            assertEquals(0L, report.countByDay().get(day));
        }
    }

    @Test
    void deveCalcularMediaContagemPorUrgenciaEDistribuicaoDiariaComZeros() {
        List<Feedback> feedbacks = List.of(
                feedback("1", 2, Urgency.CRITICA, "2026-11-02T03:00:00Z"),
                feedback("2", 5, Urgency.MEDIA, "2026-11-04T13:00:00Z"),
                feedback("3", 9, Urgency.BAIXA, "2026-11-04T20:00:00Z"));

        WeeklyReport report = aggregator.aggregate(period, feedbacks);

        assertEquals(3, report.total());
        assertEquals(new BigDecimal("5.33"), report.averageScore().orElseThrow());
        assertEquals(1L, report.countByUrgency().get(Urgency.CRITICA));
        assertEquals(1L, report.countByUrgency().get(Urgency.MEDIA));
        assertEquals(1L, report.countByUrgency().get(Urgency.BAIXA));
        assertEquals(1L, report.countByDay().get(DayOfWeek.MONDAY));
        assertEquals(2L, report.countByDay().get(DayOfWeek.WEDNESDAY));
        assertEquals(0L, report.countByDay().get(DayOfWeek.SUNDAY));
    }

    private Feedback feedback(String id, int score, Urgency urgency, String createdAt) {
        return new Feedback(id, score, urgency, "Comentário", Instant.parse(createdAt));
    }
}
