package br.com.feedflow.notifications.publishing;

import br.com.feedflow.notifications.domain.CriticalFeedback;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Objects;

public final class SnsAlertPublisher implements AlertPublisher {

    static final String SUBJECT = "[FeedFlow] Avaliacao critica recebida";

    private final SnsClient snsClient;
    private final String topicArn;
    private final AlertMessageFormatter messageFormatter;

    public SnsAlertPublisher(
            SnsClient snsClient,
            String topicArn,
            AlertMessageFormatter messageFormatter) {
        this.snsClient = Objects.requireNonNull(snsClient, "snsClient não pode ser nulo");
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalArgumentException("O ARN do tópico SNS deve ser configurado.");
        }
        this.topicArn = topicArn;
        this.messageFormatter = Objects.requireNonNull(
                messageFormatter,
                "messageFormatter não pode ser nulo");
    }

    @Override
    public void publish(CriticalFeedback feedback) throws AlertPublicationException {
        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .subject(SUBJECT)
                .message(messageFormatter.format(feedback))
                .build();
        try {
            snsClient.publish(request);
        } catch (SdkException exception) {
            throw new AlertPublicationException("A publicação no SNS falhou.", exception);
        }
    }
}
