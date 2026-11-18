package br.com.feedflow.notifications.processing;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;

import java.util.Map;
import java.util.Set;

public final class CriticalRecordSelector {

    private static final String INSERT = "INSERT";
    private static final String CRITICAL = "CRITICA";
    private static final Set<String> NON_CRITICAL_URGENCIES = Set.of("MEDIA", "BAIXA");

    public RecordSelection select(DynamodbStreamRecord record) {
        if (record == null || !INSERT.equals(record.getEventName())) {
            return RecordSelection.NON_INSERT;
        }
        if (record.getDynamodb() == null) {
            return RecordSelection.INVALID;
        }

        Map<String, AttributeValue> newImage = record.getDynamodb().getNewImage();
        if (newImage == null || newImage.isEmpty()) {
            return RecordSelection.INVALID;
        }

        AttributeValue urgencyAttribute = newImage.get("urgencia");
        String urgency = urgencyAttribute == null ? null : urgencyAttribute.getS();
        if (CRITICAL.equals(urgency)) {
            return RecordSelection.CRITICAL;
        }
        if (NON_CRITICAL_URGENCIES.contains(urgency)) {
            return RecordSelection.NON_CRITICAL;
        }
        return RecordSelection.INVALID;
    }
}
