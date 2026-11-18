package br.com.feedflow.avaliacao.persistence;

import br.com.feedflow.avaliacao.application.WeekKeyCalculator;
import br.com.feedflow.avaliacao.domain.Avaliacao;
import br.com.feedflow.avaliacao.domain.Urgencia;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.StreamViewType;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamoDbAvaliacaoRepositoryIT {

    private static final WeekKeyCalculator WEEK_KEY_CALCULATOR = new WeekKeyCalculator();
    private static DynamoDbClient client;
    private static DynamoDbAvaliacaoRepository repository;
    private static String tableName;

    @BeforeAll
    static void setUp() {
        tableName = System.getProperty("dynamodb.table", "FeedFlowFeedback");
        client = novoCliente();

        DescribeTableResponse description = client.describeTable(builder -> builder.tableName(tableName));
        assertNotNull(description.table());
        assertEquals(StreamViewType.NEW_IMAGE, description.table().streamSpecification().streamViewType());
        assertTrue(description.table().globalSecondaryIndexes().stream()
                .anyMatch(index -> DynamoDbAvaliacaoRepository.INDICE_POR_SEMANA.equals(index.indexName())
                        && ProjectionType.ALL.equals(index.projection().projectionType())));

        repository = new DynamoDbAvaliacaoRepository(client, WEEK_KEY_CALCULATOR, tableName, 2);
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2, CRITICA, Falha de áudio",
            "5, MEDIA, Ritmo adequado",
            "9, BAIXA, Aula excelente"
    })
    void devePersistirERecuperarAvaliacoesDeTodasAsUrgencias(
            int nota,
            Urgencia urgencia,
            String descricao) {
        Avaliacao avaliacao = avaliacao(nota, urgencia, descricao, Instant.now());

        Avaliacao salva = repository.salvar(avaliacao);
        Avaliacao recuperada = repository.buscarPorId(avaliacao.id()).orElseThrow();

        assertEquals(avaliacao, salva);
        assertEquals(avaliacao, recuperada);
        assertTrue(recuperada.criadoEm().toString().endsWith("Z"));
    }

    @Test
    void deveOmitirAtributoDescricaoQuandoNula() {
        Avaliacao avaliacao = avaliacao(8, Urgencia.BAIXA, null, Instant.now());
        repository.salvar(avaliacao);

        Map<String, AttributeValue> item = client.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of("id", texto(avaliacao.id().toString())))
                        .consistentRead(true)
                        .build())
                .item();

        assertFalse(item.containsKey("descricao"));
        assertEquals(avaliacao, repository.buscarPorId(avaliacao.id()).orElseThrow());
    }

    @Test
    void deveConsultarGsiPorIntervaloPercorrendoTodasAsPaginas() throws InterruptedException {
        Instant inicio = Instant.parse("2026-11-02T03:00:00Z");
        Instant fim = Instant.parse("2026-11-09T03:00:00Z");
        String weekKey = "SEMANA#2026-11-02";
        List<Avaliacao> esperadas = List.of(
                avaliacao(4, Urgencia.MEDIA, null, inicio.plusSeconds(60)),
                avaliacao(5, Urgencia.MEDIA, null, inicio.plusSeconds(120)),
                avaliacao(6, Urgencia.MEDIA, null, inicio.plusSeconds(180)),
                avaliacao(7, Urgencia.BAIXA, null, inicio.plusSeconds(240)),
                avaliacao(8, Urgencia.BAIXA, null, inicio.plusSeconds(300)));
        esperadas.forEach(repository::salvar);

        Set<UUID> idsEsperados = esperadas.stream().map(Avaliacao::id).collect(Collectors.toSet());
        List<Avaliacao> encontradas = aguardarIndice(weekKey, inicio, fim, idsEsperados);
        Set<UUID> idsEncontrados = encontradas.stream().map(Avaliacao::id).collect(Collectors.toSet());

        assertTrue(idsEncontrados.containsAll(idsEsperados));
    }

    @Test
    void deveImpedirSobrescritaAcidentalDoMesmoId() {
        Avaliacao avaliacao = avaliacao(10, Urgencia.BAIXA, null, Instant.now());
        repository.salvar(avaliacao);

        assertThrows(PersistenciaAvaliacaoException.class, () -> repository.salvar(avaliacao));
    }

    @Test
    void deveManterRegistroAoRecriarClienteComoEmReinicioDaApi() {
        Avaliacao avaliacao = avaliacao(6, Urgencia.MEDIA, null, Instant.now());

        try (DynamoDbClient primeiroCliente = novoCliente()) {
            new DynamoDbAvaliacaoRepository(primeiroCliente, WEEK_KEY_CALCULATOR, tableName, 2)
                    .salvar(avaliacao);
        }

        try (DynamoDbClient segundoCliente = novoCliente()) {
            Avaliacao recuperada = new DynamoDbAvaliacaoRepository(
                    segundoCliente,
                    WEEK_KEY_CALCULATOR,
                    tableName,
                    2).buscarPorId(avaliacao.id()).orElseThrow();
            assertEquals(avaliacao, recuperada);
        }
    }

    private static List<Avaliacao> aguardarIndice(
            String weekKey,
            Instant inicio,
            Instant fim,
            Set<UUID> idsEsperados) throws InterruptedException {
        List<Avaliacao> resultado = List.of();
        for (int tentativa = 0; tentativa < 20; tentativa++) {
            resultado = repository.buscarPorSemanaEIntervalo(weekKey, inicio, fim);
            Set<UUID> ids = resultado.stream().map(Avaliacao::id).collect(Collectors.toSet());
            if (ids.containsAll(idsEsperados)) {
                return resultado;
            }
            Thread.sleep(100);
        }
        return resultado;
    }

    private static Avaliacao avaliacao(int nota, Urgencia urgencia, String descricao, Instant criadoEm) {
        return new Avaliacao(UUID.randomUUID(), nota, urgencia, descricao, criadoEm);
    }

    private static DynamoDbClient novoCliente() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(System.getProperty("dynamodb.endpoint", "http://localhost:8000")))
                .region(Region.of(System.getProperty("dynamodb.region", "us-east-2")))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("DUMMYIDEXAMPLE", "DUMMYEXAMPLEKEY")))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    private static AttributeValue texto(String valor) {
        return AttributeValue.builder().s(valor).build();
    }
}
