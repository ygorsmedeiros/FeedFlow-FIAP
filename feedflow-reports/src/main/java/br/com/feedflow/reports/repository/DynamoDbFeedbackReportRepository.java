package br.com.feedflow.reports.repository;

import br.com.feedflow.reports.domain.Feedback;
import br.com.feedflow.reports.domain.Urgency;
import br.com.feedflow.reports.domain.WeeklyPeriod;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DynamoDbFeedbackReportRepository implements FeedbackReportRepository {

    static final String WEEK_INDEX = "byWeek";
    static final DateTimeFormatter INSTANT_FORMATTER = new DateTimeFormatterBuilder()
            .appendInstant(9)
            .toFormatter();

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbFeedbackReportRepository(DynamoDbClient client, String tableName) {
        this.client = Objects.requireNonNull(client, "client não pode ser nulo");
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName não pode estar vazio");
        }
        this.tableName = tableName;
    }

    @Override
    public List<Feedback> findByWeek(WeeklyPeriod period) {
        Objects.requireNonNull(period, "period não pode ser nulo");
        Map<String, AttributeValue> lastEvaluatedKey = null;
        List<Feedback> feedbacks = new ArrayList<>();

        try {
            do {
                QueryRequest.Builder request = QueryRequest.builder()
                        .tableName(tableName)
                        .indexName(WEEK_INDEX)
                        .keyConditionExpression("#weekKey = :weekKey AND #createdAt BETWEEN :start AND :end")
                        .expressionAttributeNames(Map.of(
                                "#weekKey", "weekKey",
                                "#createdAt", "createdAt"))
                        .expressionAttributeValues(Map.of(
                                ":weekKey", text(period.weekKey()),
                                ":start", text(format(period.startInclusive())),
                                ":end", text(format(period.endExclusive().minusNanos(1)))))
                        .scanIndexForward(true);

                if (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty()) {
                    request.exclusiveStartKey(lastEvaluatedKey);
                }

                QueryResponse response = client.query(request.build());
                response.items().stream()
                        .map(this::toDomain)
                        .forEach(feedbacks::add);
                lastEvaluatedKey = response.lastEvaluatedKey();
            } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

            return List.copyOf(feedbacks);
        } catch (RuntimeException exception) {
            throw new ReportQueryException("Não foi possível consultar as avaliações da semana.", exception);
        }
    }

    private Feedback toDomain(Map<String, AttributeValue> item) {
        return new Feedback(
                requiredString(item, "id"),
                Integer.parseInt(requiredNumber(item, "nota")),
                Urgency.valueOf(requiredString(item, "urgencia")),
                optionalString(item, "descricao"),
                Instant.parse(requiredString(item, "createdAt")));
    }

    private String requiredString(Map<String, AttributeValue> item, String attributeName) {
        AttributeValue attribute = item.get(attributeName);
        if (attribute == null || attribute.s() == null || attribute.s().isBlank()) {
            throw new IllegalArgumentException("Atributo obrigatório inválido: " + attributeName);
        }
        return attribute.s();
    }

    private String requiredNumber(Map<String, AttributeValue> item, String attributeName) {
        AttributeValue attribute = item.get(attributeName);
        if (attribute == null || attribute.n() == null || attribute.n().isBlank()) {
            throw new IllegalArgumentException("Atributo obrigatório inválido: " + attributeName);
        }
        return attribute.n();
    }

    private String optionalString(Map<String, AttributeValue> item, String attributeName) {
        AttributeValue attribute = item.get(attributeName);
        if (attribute == null || Boolean.TRUE.equals(attribute.nul())) {
            return null;
        }
        return attribute.s();
    }

    static String format(Instant instant) {
        return INSTANT_FORMATTER.format(instant);
    }

    private static AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
