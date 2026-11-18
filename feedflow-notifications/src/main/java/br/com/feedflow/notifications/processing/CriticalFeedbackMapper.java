package br.com.feedflow.notifications.processing;

import br.com.feedflow.notifications.domain.CriticalFeedback;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

public final class CriticalFeedbackMapper {

    private static final int MAX_DESCRIPTION_LENGTH = 1_000;

    public CriticalFeedback map(Map<String, AttributeValue> newImage)
            throws InvalidStreamRecordException {
        UUID id = parseId(requiredText(newImage, "id"));
        int score = parseCriticalScore(requiredNumber(newImage, "nota"));
        String urgency = requiredText(newImage, "urgencia");
        if (!"CRITICA".equals(urgency)) {
            throw new InvalidStreamRecordException("O campo urgencia não representa uma avaliação crítica.");
        }
        String description = requiredText(newImage, "descricao").trim();
        if (description.isEmpty() || description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidStreamRecordException("O campo descricao é inválido.");
        }
        Instant createdAt = parseCreatedAt(requiredText(newImage, "createdAt"));
        return new CriticalFeedback(id, score, urgency, description, createdAt);
    }

    private String requiredText(Map<String, AttributeValue> newImage, String name)
            throws InvalidStreamRecordException {
        if (newImage == null) {
            throw new InvalidStreamRecordException("NewImage está ausente.");
        }
        AttributeValue attribute = newImage.get(name);
        String value = attribute == null ? null : attribute.getS();
        if (value == null || value.isBlank()) {
            throw new InvalidStreamRecordException("O campo " + name + " está ausente ou inválido.");
        }
        return value;
    }

    private String requiredNumber(Map<String, AttributeValue> newImage, String name)
            throws InvalidStreamRecordException {
        if (newImage == null) {
            throw new InvalidStreamRecordException("NewImage está ausente.");
        }
        AttributeValue attribute = newImage.get(name);
        String value = attribute == null ? null : attribute.getN();
        if (value == null || value.isBlank()) {
            throw new InvalidStreamRecordException("O campo " + name + " está ausente ou inválido.");
        }
        return value;
    }

    private UUID parseId(String value) throws InvalidStreamRecordException {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidStreamRecordException("O campo id não contém um UUID válido.");
        }
    }

    private int parseCriticalScore(String value) throws InvalidStreamRecordException {
        try {
            int score = Integer.parseInt(value);
            if (score < 0 || score > 3) {
                throw new InvalidStreamRecordException("O campo nota está fora da faixa crítica.");
            }
            return score;
        } catch (NumberFormatException exception) {
            throw new InvalidStreamRecordException("O campo nota não contém um inteiro válido.");
        }
    }

    private Instant parseCreatedAt(String value) throws InvalidStreamRecordException {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidStreamRecordException("O campo createdAt não contém um instante válido.");
        }
    }
}
