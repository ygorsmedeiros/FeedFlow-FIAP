package br.com.feedflow.reports.publishing;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class SnsReportPublisher implements ReportPublisher {

    static final String SUBJECT = "[FeedFlow] Relatorio semanal";
    static final int MAX_MESSAGE_BYTES = 256 * 1024;

    private final SnsClient client;
    private final String topicArn;

    public SnsReportPublisher(SnsClient client, String topicArn) {
        this.client = Objects.requireNonNull(client, "client não pode ser nulo");
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalArgumentException("O ARN do tópico SNS de relatórios deve ser configurado.");
        }
        this.topicArn = topicArn;
    }

    @Override
    public void publish(String report) {
        if (report == null || report.isBlank()) {
            throw new IllegalArgumentException("report não pode estar vazio");
        }
        int messageBytes = report.getBytes(StandardCharsets.UTF_8).length;
        if (messageBytes > MAX_MESSAGE_BYTES) {
            throw new ReportPublicationException(
                    "O relatório semanal excede o limite SNS de 262144 bytes UTF-8: "
                            + messageBytes + " bytes.");
        }

        try {
            client.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(SUBJECT)
                    .message(report)
                    .build());
        } catch (SdkException exception) {
            throw new ReportPublicationException("Não foi possível publicar o relatório semanal.", exception);
        }
    }
}
