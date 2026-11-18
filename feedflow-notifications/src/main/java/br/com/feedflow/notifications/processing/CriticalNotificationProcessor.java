package br.com.feedflow.notifications.processing;

import br.com.feedflow.notifications.domain.CriticalFeedback;
import br.com.feedflow.notifications.publishing.AlertPublicationException;
import br.com.feedflow.notifications.publishing.AlertPublisher;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;

import java.util.Objects;

public final class CriticalNotificationProcessor {

    private final CriticalRecordSelector selector;
    private final CriticalFeedbackMapper mapper;
    private final AlertPublisher publisher;

    public CriticalNotificationProcessor(
            CriticalRecordSelector selector,
            CriticalFeedbackMapper mapper,
            AlertPublisher publisher) {
        this.selector = Objects.requireNonNull(selector, "selector não pode ser nulo");
        this.mapper = Objects.requireNonNull(mapper, "mapper não pode ser nulo");
        this.publisher = Objects.requireNonNull(publisher, "publisher não pode ser nulo");
    }

    public ProcessingStatus process(DynamodbStreamRecord record)
            throws AlertPublicationException {
        RecordSelection selection = selector.select(record);
        if (selection == RecordSelection.NON_INSERT
                || selection == RecordSelection.NON_CRITICAL) {
            return ProcessingStatus.IGNORED;
        }
        if (selection == RecordSelection.INVALID) {
            return ProcessingStatus.INVALID;
        }

        try {
            CriticalFeedback feedback = mapper.map(record.getDynamodb().getNewImage());
            publisher.publish(feedback);
            return ProcessingStatus.PUBLISHED;
        } catch (InvalidStreamRecordException exception) {
            return ProcessingStatus.INVALID;
        }
    }
}
