package br.com.feedflow.reports.handler;

import br.com.feedflow.reports.aggregation.WeeklyReportAggregator;
import br.com.feedflow.reports.application.WeeklyReportService;
import br.com.feedflow.reports.domain.WeeklyReport;
import br.com.feedflow.reports.formatting.WeeklyReportFormatter;
import br.com.feedflow.reports.period.PreviousWeekCalculator;
import br.com.feedflow.reports.publishing.SnsReportPublisher;
import br.com.feedflow.reports.repository.DynamoDbFeedbackReportRepository;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sns.SnsClient;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WeeklyReportHandler implements RequestHandler<Map<String, Object>, Void> {

    static final String TABLE_NAME_ENVIRONMENT_VARIABLE = "FEEDFLOW_DYNAMODB_TABLE_NAME";
    static final String TOPIC_ARN_ENVIRONMENT_VARIABLE = "FEEDFLOW_REPORT_TOPIC_ARN";

    private static final Logger LOGGER = Logger.getLogger(WeeklyReportHandler.class.getName());

    private final WeeklyReportService service;
    private final Clock clock;

    public WeeklyReportHandler() {
        this(defaultService(), Clock.systemUTC());
    }

    public WeeklyReportHandler(WeeklyReportService service, Clock clock) {
        this.service = Objects.requireNonNull(service, "service não pode ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock não pode ser nulo");
    }

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        try {
            Instant reference = eventTime(event);
            WeeklyReport report = service.generateAndPublish(reference);
            LOGGER.info("Relatório semanal publicado. weekKey=" + report.period().weekKey()
                    + ", total=" + report.total());
            return null;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Falha na geração ou publicação do relatório semanal.", exception);
            throw exception;
        }
    }

    private Instant eventTime(Map<String, Object> event) {
        if (event == null || !event.containsKey("time")) {
            return clock.instant();
        }
        Object value = event.get("time");
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("O campo time do evento agendado é inválido.");
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("O campo time do evento agendado é inválido.", exception);
        }
    }

    private static WeeklyReportService defaultService() {
        String tableName = requiredEnvironmentVariable(TABLE_NAME_ENVIRONMENT_VARIABLE);
        String topicArn = requiredEnvironmentVariable(TOPIC_ARN_ENVIRONMENT_VARIABLE);
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        SnsClient snsClient = SnsClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();

        return new WeeklyReportService(
                new PreviousWeekCalculator(),
                new DynamoDbFeedbackReportRepository(dynamoDbClient, tableName),
                new WeeklyReportAggregator(),
                new WeeklyReportFormatter(),
                new SnsReportPublisher(snsClient, topicArn));
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória ausente: " + name);
        }
        return value;
    }
}
