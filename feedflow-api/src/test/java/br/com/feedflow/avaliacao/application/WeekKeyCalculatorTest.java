package br.com.feedflow.avaliacao.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeekKeyCalculatorTest {

    private final WeekKeyCalculator calculator = new WeekKeyCalculator();

    @Test
    void deveUsarSegundaFeiraDaSemanaNoFusoDeSaoPaulo() {
        assertEquals(
                "SEMANA#2026-10-26",
                calculator.calcular(Instant.parse("2026-11-01T12:00:00Z")));
    }

    @Test
    void deveMudarSemanaExatamenteNaMeiaNoiteLocalDeSegundaFeira() {
        assertEquals(
                "SEMANA#2026-10-26",
                calculator.calcular(Instant.parse("2026-11-02T02:59:59.999999999Z")));
        assertEquals(
                "SEMANA#2026-11-02",
                calculator.calcular(Instant.parse("2026-11-02T03:00:00Z")));
    }
}
