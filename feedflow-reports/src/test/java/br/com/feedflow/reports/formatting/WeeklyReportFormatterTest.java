package br.com.feedflow.reports.formatting;

import br.com.feedflow.reports.aggregation.WeeklyReportAggregator;
import br.com.feedflow.reports.domain.Feedback;
import br.com.feedflow.reports.domain.Urgency;
import br.com.feedflow.reports.domain.WeeklyPeriod;
import br.com.feedflow.reports.domain.WeeklyReport;
import br.com.feedflow.reports.period.PreviousWeekCalculator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyReportFormatterTest {

    private final WeeklyReportAggregator aggregator = new WeeklyReportAggregator();
    private final WeeklyReportFormatter formatter = new WeeklyReportFormatter();
    private final WeeklyPeriod period = new PreviousWeekCalculator()
            .calculate(Instant.parse("2026-11-12T15:00:00Z"));

    @Test
    void deveFormatarDescricaoAusenteComTextoPadrao() {
        Feedback feedback = new Feedback(
                "1",
                8,
                Urgency.BAIXA,
                null,
                Instant.parse("2026-11-02T13:00:00Z"));
        WeeklyReport report = aggregator.aggregate(period, List.of(feedback));

        String formatted = formatter.format(report);

        assertTrue(formatted.contains("Descrição: Avaliação sem comentário"));
        assertTrue(formatted.contains("02/11/2026 10:00:00 -03:00"));
        assertTrue(formatted.contains("Média das notas: 8,00"));
    }

    @Test
    void deveInformarMediaIndisponivelQuandoNaoHouverAvaliacoes() {
        String formatted = formatter.format(aggregator.aggregate(period, List.of()));

        assertTrue(formatted.contains("Total de avaliações: 0"));
        assertTrue(formatted.contains("Média das notas: N/A"));
        assertTrue(formatted.contains("Nenhuma avaliação registrada no período."));
    }
}
