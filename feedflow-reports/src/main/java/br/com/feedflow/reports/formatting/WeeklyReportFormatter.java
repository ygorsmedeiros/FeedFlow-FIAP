package br.com.feedflow.reports.formatting;

import br.com.feedflow.reports.domain.Feedback;
import br.com.feedflow.reports.domain.Urgency;
import br.com.feedflow.reports.domain.WeeklyReport;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

public final class WeeklyReportFormatter {

    public static final String MISSING_DESCRIPTION = "Avaliação sem comentário";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/uuuu HH:mm:ss XXX");
    private static final Map<DayOfWeek, String> DAY_LABELS = Map.of(
            DayOfWeek.MONDAY, "Segunda-feira",
            DayOfWeek.TUESDAY, "Terça-feira",
            DayOfWeek.WEDNESDAY, "Quarta-feira",
            DayOfWeek.THURSDAY, "Quinta-feira",
            DayOfWeek.FRIDAY, "Sexta-feira",
            DayOfWeek.SATURDAY, "Sábado",
            DayOfWeek.SUNDAY, "Domingo");

    public String format(WeeklyReport report) {
        Objects.requireNonNull(report, "report não pode ser nulo");

        StringBuilder message = new StringBuilder();
        message.append("Relatório semanal FeedFlow\n")
                .append("Período: ")
                .append(DATE_FORMATTER.format(report.period().startDate()))
                .append(" a ")
                .append(DATE_FORMATTER.format(report.period().endDateExclusive().minusDays(1)))
                .append(" (America/Sao_Paulo)\n\n")
                .append("Total de avaliações: ").append(report.total()).append('\n')
                .append("Média das notas: ")
                .append(report.averageScore()
                        .map(average -> average.toPlainString().replace('.', ','))
                        .orElse("N/A"))
                .append("\n\nQuantidade por urgência:\n");

        for (Urgency urgency : Urgency.values()) {
            message.append("- ").append(urgency).append(": ")
                    .append(report.countByUrgency().get(urgency)).append('\n');
        }

        message.append("\nQuantidade por dia:\n");
        for (DayOfWeek day : DayOfWeek.values()) {
            message.append("- ").append(DAY_LABELS.get(day)).append(": ")
                    .append(report.countByDay().get(day)).append('\n');
        }

        message.append("\nAvaliações:\n");
        if (report.feedbacks().isEmpty()) {
            message.append("Nenhuma avaliação registrada no período.\n");
        } else {
            for (Feedback feedback : report.feedbacks()) {
                appendFeedback(message, feedback, report);
            }
        }
        return message.toString();
    }

    private void appendFeedback(StringBuilder message, Feedback feedback, WeeklyReport report) {
        String description = feedback.description();
        if (description == null || description.isBlank()) {
            description = MISSING_DESCRIPTION;
        }
        description = description.replace('\r', ' ').replace('\n', ' ');

        message.append("- ")
                .append(DATE_TIME_FORMATTER.format(feedback.createdAt().atZone(report.period().zone())))
                .append(" | Nota: ").append(feedback.score())
                .append(" | Urgência: ").append(feedback.urgency())
                .append(" | Descrição: ").append(description)
                .append('\n');
    }
}
