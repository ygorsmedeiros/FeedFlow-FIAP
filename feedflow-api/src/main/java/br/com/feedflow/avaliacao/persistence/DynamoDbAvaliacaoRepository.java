package br.com.feedflow.avaliacao.persistence;

import br.com.feedflow.avaliacao.application.WeekKeyCalculator;
import br.com.feedflow.avaliacao.domain.Avaliacao;
import br.com.feedflow.avaliacao.domain.Urgencia;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DynamoDbAvaliacaoRepository implements AvaliacaoRepository {

    static final String INDICE_POR_SEMANA = "byWeek";
    static final DateTimeFormatter INSTANT_FORMATTER = new DateTimeFormatterBuilder()
            .appendInstant(9)
            .toFormatter();

    private final DynamoDbClient client;
    private final WeekKeyCalculator weekKeyCalculator;
    private final String tableName;
    private final int queryPageSize;

    @Inject
    public DynamoDbAvaliacaoRepository(
            DynamoDbClient client,
            WeekKeyCalculator weekKeyCalculator,
            DynamoDbConfig config) {
        this(client, weekKeyCalculator, config.tableName(), config.queryPageSize());
    }

    DynamoDbAvaliacaoRepository(
            DynamoDbClient client,
            WeekKeyCalculator weekKeyCalculator,
            String tableName,
            int queryPageSize) {
        this.client = Objects.requireNonNull(client, "client não pode ser nulo");
        this.weekKeyCalculator = Objects.requireNonNull(weekKeyCalculator, "weekKeyCalculator não pode ser nulo");
        this.tableName = Objects.requireNonNull(tableName, "tableName não pode ser nulo");
        if (tableName.isBlank()) {
            throw new IllegalArgumentException("tableName não pode estar vazio");
        }
        if (queryPageSize <= 0) {
            throw new IllegalArgumentException("queryPageSize deve ser positivo");
        }
        this.queryPageSize = queryPageSize;
    }

    @Override
    public Avaliacao salvar(Avaliacao avaliacao) {
        Objects.requireNonNull(avaliacao, "avaliacao não pode ser nula");

        try {
            client.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(paraItem(avaliacao))
                    .conditionExpression("attribute_not_exists(#id)")
                    .expressionAttributeNames(Map.of("#id", "id"))
                    .build());
            return avaliacao;
        } catch (ConditionalCheckFailedException exception) {
            throw new PersistenciaAvaliacaoException("Já existe uma avaliação com o ID informado.", exception);
        } catch (SdkException exception) {
            throw new PersistenciaAvaliacaoException("Não foi possível persistir a avaliação.", exception);
        }
    }

    @Override
    public Optional<Avaliacao> buscarPorId(UUID id) {
        Objects.requireNonNull(id, "id não pode ser nulo");

        try {
            GetItemResponse response = client.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("id", texto(id.toString())))
                    .consistentRead(true)
                    .build());

            return response.hasItem() && !response.item().isEmpty()
                    ? Optional.of(paraDominio(response.item()))
                    : Optional.empty();
        } catch (SdkException exception) {
            throw new PersistenciaAvaliacaoException("Não foi possível consultar a avaliação.", exception);
        }
    }

    @Override
    public List<Avaliacao> buscarPorSemanaEIntervalo(
            String weekKey,
            Instant inicioInclusivo,
            Instant fimExclusivo) {
        validarIntervalo(weekKey, inicioInclusivo, fimExclusivo);

        Map<String, AttributeValue> ultimaChave = null;
        List<Avaliacao> avaliacoes = new ArrayList<>();
        Instant fimInclusivo = fimExclusivo.minusNanos(1);

        try {
            do {
                QueryRequest.Builder request = QueryRequest.builder()
                        .tableName(tableName)
                        .indexName(INDICE_POR_SEMANA)
                        .keyConditionExpression("#weekKey = :weekKey AND #createdAt BETWEEN :inicio AND :fim")
                        .expressionAttributeNames(Map.of(
                                "#weekKey", "weekKey",
                                "#createdAt", "createdAt"))
                        .expressionAttributeValues(Map.of(
                                ":weekKey", texto(weekKey),
                                ":inicio", texto(formatar(inicioInclusivo)),
                                ":fim", texto(formatar(fimInclusivo))))
                        .limit(queryPageSize)
                        .scanIndexForward(true);

                if (ultimaChave != null && !ultimaChave.isEmpty()) {
                    request.exclusiveStartKey(ultimaChave);
                }

                QueryResponse response = client.query(request.build());
                response.items().stream()
                        .map(this::paraDominio)
                        .forEach(avaliacoes::add);
                ultimaChave = response.lastEvaluatedKey();
            } while (ultimaChave != null && !ultimaChave.isEmpty());

            return List.copyOf(avaliacoes);
        } catch (SdkException exception) {
            throw new PersistenciaAvaliacaoException("Não foi possível consultar as avaliações da semana.", exception);
        }
    }

    private Map<String, AttributeValue> paraItem(Avaliacao avaliacao) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", texto(avaliacao.id().toString()));
        item.put("nota", numero(avaliacao.nota()));
        item.put("urgencia", texto(avaliacao.urgencia().name()));
        item.put("createdAt", texto(formatar(avaliacao.criadoEm())));
        item.put("weekKey", texto(weekKeyCalculator.calcular(avaliacao.criadoEm())));
        if (avaliacao.descricao() != null) {
            item.put("descricao", texto(avaliacao.descricao()));
        }
        return item;
    }

    private Avaliacao paraDominio(Map<String, AttributeValue> item) {
        AttributeValue descricao = item.get("descricao");
        return new Avaliacao(
                UUID.fromString(item.get("id").s()),
                Integer.parseInt(item.get("nota").n()),
                Urgencia.valueOf(item.get("urgencia").s()),
                descricao == null || Boolean.TRUE.equals(descricao.nul()) ? null : descricao.s(),
                Instant.parse(item.get("createdAt").s()));
    }

    private void validarIntervalo(String weekKey, Instant inicioInclusivo, Instant fimExclusivo) {
        if (weekKey == null || weekKey.isBlank()) {
            throw new IllegalArgumentException("weekKey não pode estar vazio");
        }
        Objects.requireNonNull(inicioInclusivo, "inicioInclusivo não pode ser nulo");
        Objects.requireNonNull(fimExclusivo, "fimExclusivo não pode ser nulo");
        if (!inicioInclusivo.isBefore(fimExclusivo)) {
            throw new IllegalArgumentException("O início deve ser anterior ao fim do intervalo");
        }
    }

    static String formatar(Instant instant) {
        return INSTANT_FORMATTER.format(instant);
    }

    private static AttributeValue texto(String valor) {
        return AttributeValue.builder().s(valor).build();
    }

    private static AttributeValue numero(int valor) {
        return AttributeValue.builder().n(Integer.toString(valor)).build();
    }
}
