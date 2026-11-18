package br.com.feedflow.avaliacao.api;

import br.com.feedflow.avaliacao.domain.Avaliacao;
import br.com.feedflow.avaliacao.persistence.AvaliacaoRepository;
import br.com.feedflow.avaliacao.persistence.PersistenciaAvaliacaoException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@QuarkusTest
class AvaliacaoResourceTest {

    @InjectMock
    AvaliacaoRepository repository;

    @BeforeEach
    void setUp() {
        reset(repository);
        when(repository.salvar(any(Avaliacao.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void deveRetornar201SomenteDepoisDaPersistenciaConfirmada() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"nota\": 4}")
        .when()
                .post("/avaliacao")
        .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .body("nota", equalTo(4))
                .body("urgencia", equalTo("MEDIA"))
                .body("descricao", equalTo(null))
                .body("criadoEm", notNullValue())
                .body("mensagem", equalTo("Avaliação registrada sem comentário."))
                .body("$", not(hasKey("stackTrace")));
    }

    @Test
    void deveRejeitarARotaUnicodeRemovidaDoContrato() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"nota\": 7, \"descricao\": null}")
        .when()
                .post("/avaliação")
        .then()
                .statusCode(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"nota\": -1, \"descricao\": \"fora do limite\"}",
            "{\"nota\": 11}",
            "{}",
            "{\"nota\": null}",
            "{\"nota\": 5.5}",
            "{\"nota\": \"5\"}",
            "{\"nota\": true}"
    })
    void deveRetornar400ParaNotaInvalida(String corpo) {
        given()
                .contentType(ContentType.JSON)
                .body(corpo)
        .when()
                .post("/avaliacao")
        .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("codigo", equalTo("AVALIACAO_INVALIDA"))
                .body("$", not(hasKey("stackTrace")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"nota\": 0}",
            "{\"nota\": 3, \"descricao\": null}",
            "{\"nota\": 2, \"descricao\": \"\"}",
            "{\"nota\": 1, \"descricao\": \"   \"}"
    })
    void deveRetornar400ParaAvaliacaoCriticaSemDescricao(String corpo) {
        given()
                .contentType(ContentType.JSON)
                .body(corpo)
        .when()
                .post("/avaliacao")
        .then()
                .statusCode(400)
                .body("codigo", equalTo("AVALIACAO_INVALIDA"))
                .body("mensagem", equalTo("A descrição é obrigatória para avaliações críticas."));
    }

    @Test
    void deveConsiderarValidaDescricaoComMilCaracteres() {
        String corpo = "{\"nota\": 3, \"descricao\": \"" + "a".repeat(1_000) + "\"}";

        given()
                .contentType(ContentType.JSON)
                .body(corpo)
        .when()
                .post("/avaliacao")
        .then()
                .statusCode(201);
    }

    @Test
    void deveRejeitarDescricaoComMilEUmCaracteres() {
        String corpo = "{\"nota\": 8, \"descricao\": \"" + "a".repeat(1_001) + "\"}";

        given()
                .contentType(ContentType.JSON)
                .body(corpo)
        .when()
                .post("/avaliacao")
        .then()
                .statusCode(400)
                .body("codigo", equalTo("AVALIACAO_INVALIDA"));
    }

    @Test
    void deveRejeitarDescricaoComTipoIncompativel() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"nota\": 8, \"descricao\": 123}")
        .when()
                .post("/avaliacao")
        .then()
                .statusCode(400)
                .body("codigo", equalTo("AVALIACAO_INVALIDA"));
    }

    @Test
    void deveRetornarErroGenericoSemFalso201QuandoPersistenciaFalha() {
        when(repository.salvar(any(Avaliacao.class)))
                .thenThrow(new PersistenciaAvaliacaoException(
                        "endpoint interno não deve aparecer",
                        new IllegalStateException("credencial não deve aparecer")));

        given()
                .contentType(ContentType.JSON)
                .body("{\"nota\": 8}")
        .when()
                .post("/avaliacao")
        .then()
                .statusCode(500)
                .contentType(ContentType.JSON)
                .body("codigo", equalTo("ERRO_PERSISTENCIA"))
                .body("mensagem", equalTo("Não foi possível registrar a avaliação."))
                .body("$", not(hasKey("stackTrace")));
    }

}
