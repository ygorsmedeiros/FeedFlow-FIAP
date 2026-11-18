package br.com.feedflow.reports.application;

import br.com.feedflow.reports.aggregation.WeeklyReportAggregator;
import br.com.feedflow.reports.domain.Feedback;
import br.com.feedflow.reports.domain.WeeklyPeriod;
import br.com.feedflow.reports.domain.WeeklyReport;
import br.com.feedflow.reports.formatting.WeeklyReportFormatter;
import br.com.feedflow.reports.period.PreviousWeekCalculator;
import br.com.feedflow.reports.publishing.ReportPublisher;
import br.com.feedflow.reports.repository.FeedbackReportRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class WeeklyReportService {

    private final PreviousWeekCalculator periodCalculator;
    private final FeedbackReportRepository repository;
    private final WeeklyReportAggregator aggregator;
    private final WeeklyReportFormatter formatter;
    private final ReportPublisher publisher;

    public WeeklyReportService(
            PreviousWeekCalculator periodCalculator,
            FeedbackReportRepository repository,
            WeeklyReportAggregator aggregator,
            WeeklyReportFormatter formatter,
            ReportPublisher publisher) {
        this.periodCalculator = Objects.requireNonNull(periodCalculator, "periodCalculator não pode ser nulo");
        this.repository = Objects.requireNonNull(repository, "repository não pode ser nulo");
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator não pode ser nulo");
        this.formatter = Objects.requireNonNull(formatter, "formatter não pode ser nulo");
        this.publisher = Objects.requireNonNull(publisher, "publisher não pode ser nulo");
    }

    public WeeklyReport generateAndPublish(Instant reference) {
        WeeklyPeriod period = periodCalculator.calculate(reference);
        List<Feedback> feedbacks = repository.findByWeek(period);
        WeeklyReport report = aggregator.aggregate(period, feedbacks);
        publisher.publish(formatter.format(report));
        return report;
    }
}
