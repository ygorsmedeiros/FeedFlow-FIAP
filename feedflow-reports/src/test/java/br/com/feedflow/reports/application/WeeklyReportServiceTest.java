package br.com.feedflow.reports.application;

import br.com.feedflow.reports.aggregation.WeeklyReportAggregator;
import br.com.feedflow.reports.domain.WeeklyReport;
import br.com.feedflow.reports.formatting.WeeklyReportFormatter;
import br.com.feedflow.reports.period.PreviousWeekCalculator;
import br.com.feedflow.reports.publishing.ReportPublicationException;
import br.com.feedflow.reports.publishing.ReportPublisher;
import br.com.feedflow.reports.repository.FeedbackReportRepository;
import br.com.feedflow.reports.repository.ReportQueryException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyReportServiceTest {

    @Test
    void deveConsultarGerarEPublicarRelatorioMesmoSemAvaliacoes() {
        AtomicReference<String> publishedMessage = new AtomicReference<>();
        FeedbackReportRepository repository = period -> List.of();
        ReportPublisher publisher = publishedMessage::set;
        WeeklyReportService service = new WeeklyReportService(
                new PreviousWeekCalculator(),
                repository,
                new WeeklyReportAggregator(),
                new WeeklyReportFormatter(),
                publisher);

        WeeklyReport report = service.generateAndPublish(Instant.parse("2026-11-12T15:00:00Z"));

        assertEquals(0, report.total());
        assertEquals("SEMANA#2026-11-02", report.period().weekKey());
        assertTrue(publishedMessage.get().contains("Total de avaliações: 0"));
        assertTrue(publishedMessage.get().contains("Média das notas: N/A"));
    }

    @Test
    void devePropagarFalhaDeConsultaSemPublicar() {
        FeedbackReportRepository repository = period -> {
            throw new ReportQueryException("falha simulada", new IllegalStateException());
        };
        AtomicReference<String> publishedMessage = new AtomicReference<>();
        WeeklyReportService service = service(repository, publishedMessage::set);

        assertThrows(
                ReportQueryException.class,
                () -> service.generateAndPublish(Instant.parse("2026-11-12T15:00:00Z")));
        assertNull(publishedMessage.get());
    }

    @Test
    void devePropagarFalhaDePublicacaoSns() {
        FeedbackReportRepository repository = period -> List.of();
        ReportPublisher publisher = report -> {
            throw new ReportPublicationException("falha simulada", new IllegalStateException());
        };
        WeeklyReportService service = service(repository, publisher);

        assertThrows(
                ReportPublicationException.class,
                () -> service.generateAndPublish(Instant.parse("2026-11-12T15:00:00Z")));
    }

    private WeeklyReportService service(
            FeedbackReportRepository repository,
            ReportPublisher publisher) {
        return new WeeklyReportService(
                new PreviousWeekCalculator(),
                repository,
                new WeeklyReportAggregator(),
                new WeeklyReportFormatter(),
                publisher);
    }
}
