package br.com.feedflow.notifications.handler;

import br.com.feedflow.notifications.processing.CriticalFeedbackMapper;
import br.com.feedflow.notifications.processing.CriticalNotificationProcessor;
import br.com.feedflow.notifications.processing.CriticalRecordSelector;
import br.com.feedflow.notifications.processing.ProcessingStatus;
import br.com.feedflow.notifications.publishing.AlertMessageFormatter;
import br.com.feedflow.notifications.publishing.AlertPublicationException;
import br.com.feedflow.notifications.publishing.SnsAlertPublisher;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sns.SnsClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CriticalNotificationHandler
        implements RequestHandler<DynamodbEvent, StreamsEventResponse> {

    static final String TOPIC_ARN_ENVIRONMENT_VARIABLE = "FEEDFLOW_ALERT_TOPIC_ARN";

    private static final Logger LOGGER = Logger.getLogger(CriticalNotificationHandler.class.getName());

    private final CriticalNotificationProcessor processor;

    public CriticalNotificationHandler() {
        this(defaultProcessor());
    }

    public CriticalNotificationHandler(CriticalNotificationProcessor processor) {
        this.processor = Objects.requireNonNull(processor, "processor não pode ser nulo");
    }

    @Override
    public StreamsEventResponse handleRequest(DynamodbEvent event, Context context) {
        List<StreamsEventResponse.BatchItemFailure> failures = new ArrayList<>();
        boolean unreportableFailure = false;
        int published = 0;
        int ignored = 0;
        int invalid = 0;

        List<DynamodbStreamRecord> records = event == null ? null : event.getRecords();
        if (records == null || records.isEmpty()) {
            return new StreamsEventResponse(failures);
        }

        for (DynamodbStreamRecord record : records) {
            try {
                ProcessingStatus status = processor.process(record);
                switch (status) {
                    case PUBLISHED -> published++;
                    case IGNORED -> ignored++;
                    case INVALID -> {
                        invalid++;
                        LOGGER.warning(() -> "Registro permanente inválido descartado. identificador="
                                + safeIdentifier(record));
                    }
                }
            } catch (AlertPublicationException | RuntimeException exception) {
                String sequenceNumber = sequenceNumber(record);
                LOGGER.log(
                        Level.SEVERE,
                        "Falha técnica no processamento do registro. identificador="
                                + safeIdentifier(record),
                        exception);
                if (sequenceNumber == null || sequenceNumber.isBlank()) {
                    unreportableFailure = true;
                } else {
                    failures.add(new StreamsEventResponse.BatchItemFailure(sequenceNumber));
                }
            }
        }

        LOGGER.info("Lote processado. publicados=" + published
                + ", ignorados=" + ignored
                + ", invalidos=" + invalid
                + ", falhasTecnicas=" + failures.size());

        if (unreportableFailure) {
            throw new IllegalStateException(
                    "Falha técnica sem sequenceNumber; o lote inteiro será reprocessado.");
        }
        return new StreamsEventResponse(failures);
    }

    private static CriticalNotificationProcessor defaultProcessor() {
        SnsClient snsClient = SnsClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        String topicArn = System.getenv(TOPIC_ARN_ENVIRONMENT_VARIABLE);
        return new CriticalNotificationProcessor(
                new CriticalRecordSelector(),
                new CriticalFeedbackMapper(),
                new SnsAlertPublisher(snsClient, topicArn, new AlertMessageFormatter()));
    }

    private String safeIdentifier(DynamodbStreamRecord record) {
        String sequenceNumber = sequenceNumber(record);
        if (sequenceNumber != null && !sequenceNumber.isBlank()) {
            return sequenceNumber;
        }
        if (record != null && record.getEventID() != null && !record.getEventID().isBlank()) {
            return record.getEventID();
        }
        return "indisponivel";
    }

    private String sequenceNumber(DynamodbStreamRecord record) {
        if (record == null || record.getDynamodb() == null) {
            return null;
        }
        return record.getDynamodb().getSequenceNumber();
    }
}
