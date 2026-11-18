import br.com.feedflow.reports.aggregation.WeeklyReportAggregator;
import br.com.feedflow.reports.application.WeeklyReportService;
import br.com.feedflow.reports.domain.Urgency;
import br.com.feedflow.reports.formatting.WeeklyReportFormatter;
import br.com.feedflow.reports.period.PreviousWeekCalculator;
import br.com.feedflow.reports.repository.DynamoDbFeedbackReportRepository;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

class WeeklyReportDemo {
    private static final String WEEK = "SEMANA#2026-11-09";

    public static void main(String[] args) {
        int port = args.length == 0 ? 8000 : Integer.parseInt(args[0]);
        String table = "FeedFlowDemo-" + UUID.randomUUID();
        try (var client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://127.0.0.1:" + port))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .region(Region.US_EAST_2)
                .build()) {
            client.createTable(b -> b.tableName(table)
                    .billingMode("PAY_PER_REQUEST")
                    .attributeDefinitions(attribute("id"), attribute("weekKey"), attribute("createdAt"))
                    .keySchema(key("id", KeyType.HASH))
                    .globalSecondaryIndexes(g -> g.indexName("byWeek")
                            .keySchema(key("weekKey", KeyType.HASH), key("createdAt", KeyType.RANGE))
                            .projection(p -> p.projectionType("ALL"))));
            try {
                put(client, table, "2026-11-09T13:00:00.000000000Z", 2, Urgency.CRITICA, "aula confusa");
                put(client, table, "2026-11-09T18:00:00.000000000Z", 6, Urgency.MEDIA, "ritmo razoável");
                put(client, table, "2026-11-12T14:00:00.000000000Z", 9, Urgency.BAIXA, "boa aula");

                var report = new WeeklyReportService(new PreviousWeekCalculator(),
                        new DynamoDbFeedbackReportRepository(client, table), new WeeklyReportAggregator(),
                        new WeeklyReportFormatter(), System.out::println)
                        .generateAndPublish(Instant.parse("2026-11-16T15:00:00Z"));
                if (report.total() != 3 || !report.averageScore().orElseThrow().toPlainString().equals("5.67")
                        || report.countByDay().get(DayOfWeek.MONDAY) != 2L
                        || report.countByDay().get(DayOfWeek.THURSDAY) != 1L
                        || report.countByUrgency().values().stream().anyMatch(count -> count != 1L)) {
                    throw new IllegalStateException("Os totais da amostra divergiram");
                }
            } finally {
                client.deleteTable(b -> b.tableName(table));
            }
        }
    }

    private static void put(DynamoDbClient client, String table, String timestamp, int score,
                            Urgency urgency, String description) {
        client.putItem(b -> b.tableName(table).conditionExpression("attribute_not_exists(id)").item(Map.of(
                "id", text(UUID.randomUUID().toString()),
                "weekKey", text(WEEK),
                "createdAt", text(timestamp),
                "nota", AttributeValue.fromN(Integer.toString(score)),
                "urgencia", text(urgency.name()),
                "descricao", text("[DEMO] " + description))));
    }

    private static AttributeValue text(String value) {
        return AttributeValue.fromS(value);
    }

    private static AttributeDefinition attribute(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType("S").build();
    }

    private static KeySchemaElement key(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }
}
