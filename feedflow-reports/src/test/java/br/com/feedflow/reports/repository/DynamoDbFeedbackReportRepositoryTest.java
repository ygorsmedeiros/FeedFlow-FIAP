package br.com.feedflow.reports.repository;

import br.com.feedflow.reports.domain.Feedback;
import br.com.feedflow.reports.domain.WeeklyPeriod;
import br.com.feedflow.reports.period.PreviousWeekCalculator;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamoDbFeedbackReportRepositoryTest {

    private final WeeklyPeriod period = new PreviousWeekCalculator()
            .calculate(Instant.parse("2026-11-12T15:00:00Z"));

    @Test
    void deveConsultarIndicePorSemanaEConsumirTodasAsPaginas() {
        Map<String, AttributeValue> paginationKey = Map.of(
                "id", text("1"),
                "weekKey", text(period.weekKey()),
                "createdAt", text("2026-11-02T13:00:00.000000000Z"));
        List<QueryRequest> capturedRequests = new ArrayList<>();
        Deque<QueryResponse> responses = new ArrayDeque<>(List.of(
                QueryResponse.builder()
                        .items(List.of(item("1", "8", "BAIXA", "2026-11-02T13:00:00.000000000Z", true)))
                        .lastEvaluatedKey(paginationKey)
                        .build(),
                QueryResponse.builder()
                        .items(List.of(item("2", "2", "CRITICA", "2026-11-03T13:00:00.000000000Z", false)))
                        .lastEvaluatedKey(Map.of())
                        .build()));
        DynamoDbClient client = dynamoDbClient((QueryRequest request) -> {
            capturedRequests.add(request);
            return responses.removeFirst();
        });
        DynamoDbFeedbackReportRepository repository =
                new DynamoDbFeedbackReportRepository(client, "FeedFlowFeedback");

        List<Feedback> feedbacks = repository.findByWeek(period);

        assertEquals(2, feedbacks.size());
        assertEquals("Avaliação válida", feedbacks.get(0).description());
        assertNull(feedbacks.get(1).description());

        assertEquals(2, capturedRequests.size());
        QueryRequest firstRequest = capturedRequests.get(0);
        QueryRequest secondRequest = capturedRequests.get(1);
        assertEquals("FeedFlowFeedback", firstRequest.tableName());
        assertEquals("byWeek", firstRequest.indexName());
        assertEquals(period.weekKey(), firstRequest.expressionAttributeValues().get(":weekKey").s());
        assertEquals(paginationKey, secondRequest.exclusiveStartKey());
    }

    @Test
    void deveFalharQuandoAConsultaDynamoDbFalhar() {
        DynamoDbClient client = dynamoDbClient(request -> {
            throw DynamoDbException.builder().message("falha simulada").build();
        });
        DynamoDbFeedbackReportRepository repository =
                new DynamoDbFeedbackReportRepository(client, "FeedFlowFeedback");

        assertThrows(ReportQueryException.class, () -> repository.findByWeek(period));
    }

    private Map<String, AttributeValue> item(
            String id,
            String score,
            String urgency,
            String createdAt,
            boolean withDescription) {
        Map<String, AttributeValue> item = new java.util.HashMap<>();
        item.put("id", text(id));
        item.put("nota", number(score));
        item.put("urgencia", text(urgency));
        item.put("createdAt", text(createdAt));
        item.put("weekKey", text(period.weekKey()));
        if (withDescription) {
            item.put("descricao", text("Avaliação válida"));
        }
        return item;
    }

    private static AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue number(String value) {
        return AttributeValue.builder().n(value).build();
    }

    private DynamoDbClient dynamoDbClient(QueryOperation queryOperation) {
        return (DynamoDbClient) Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class<?>[]{DynamoDbClient.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("query")
                            && arguments != null
                            && arguments.length == 1
                            && arguments[0] instanceof QueryRequest request) {
                        return queryOperation.execute(request);
                    }
                    if (method.getName().equals("serviceName")) {
                        return "dynamodb";
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    throw new UnsupportedOperationException("Operação não simulada: " + method.getName());
                });
    }

    @FunctionalInterface
    private interface QueryOperation {
        QueryResponse execute(QueryRequest request);
    }
}
