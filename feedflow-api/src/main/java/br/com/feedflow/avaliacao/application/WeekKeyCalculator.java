package br.com.feedflow.avaliacao.application;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

@ApplicationScoped
public class WeekKeyCalculator {

    public static final ZoneId ZONA_RELATORIO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public String calcular(Instant criadoEm) {
        Objects.requireNonNull(criadoEm, "criadoEm não pode ser nulo");

        LocalDate segundaFeira = criadoEm.atZone(ZONA_RELATORIO)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        return "SEMANA#" + DATA_FORMATTER.format(segundaFeira);
    }
}
