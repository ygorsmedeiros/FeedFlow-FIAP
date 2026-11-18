package br.com.feedflow.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class HealthResourceTest {

    @Test
    void shouldReturnApplicationHealth() {
        given()
                .when().get("/health")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("UP"))
                .body("application", is("FeedFlow"));
    }
}
