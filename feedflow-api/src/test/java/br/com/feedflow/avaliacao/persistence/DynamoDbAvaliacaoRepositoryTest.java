package br.com.feedflow.avaliacao.persistence;

import br.com.feedflow.avaliacao.application.WeekKeyCalculator;
import br.com.feedflow.avaliacao.domain.Avaliacao;
import br.com.feedflow.avaliacao.domain.Urgencia;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamoDbAvaliacaoRepositoryTest {

    @Test
    void deveConsumirTodasAsPaginasDaQuery() {
        DynamoDbClient client = mock(DynamoDbClient.class);
        Map<String, AttributeValue> chavePaginacao = Map.of("id", texto(UUID.randomUUID().toString()));
        when(client.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder()
                        .items(List.of(item(Instant.parse("2026-11-02T10:00:00Z"))))
                        .lastEvaluatedKey(chavePaginacao)
                        .build())
                .thenReturn(QueryResponse.builder()
                        .items(List.of(item(Instant.parse("2026-11-02T11:00:00Z"))))
                        .lastEvaluatedKey(Map.of())
                        .build());

        DynamoDbAvaliacaoRepository repository = new DynamoDbAvaliacaoRepository(
                client,
                new WeekKeyCalculator(),
                "FeedFlowFeedback",
                1);

        List<Avaliacao> resultado = repository.buscarPorSemanaEIntervalo(
                "SEMANA#2026-11-02",
                Instant.parse("2026-11-02T03:00:00Z"),
                Instant.parse("2026-11-09T03:00:00Z"));

        assertEquals(2, resultado.size());
        verify(client, times(2)).query(any(QueryRequest.class));
    }

    @Test
    void deveFormatarInstanteComPrecisaoFixaParaOrdenacaoLexicografica() {
        assertEquals(
                "2026-11-02T03:00:00.000000000Z",
                DynamoDbAvaliacaoRepository.formatar(Instant.parse("2026-11-02T03:00:00Z")));
    }

    private static Map<String, AttributeValue> item(Instant criadoEm) {
        return Map.of(
                "id", texto(UUID.randomUUID().toString()),
                "nota", AttributeValue.builder().n("8").build(),
                "urgencia", texto(Urgencia.BAIXA.name()),
                "createdAt", texto(DynamoDbAvaliacaoRepository.formatar(criadoEm)),
                "weekKey", texto("SEMANA#2026-11-02"));
    }

    private static AttributeValue texto(String valor) {
        return AttributeValue.builder().s(valor).build();
    }
}
