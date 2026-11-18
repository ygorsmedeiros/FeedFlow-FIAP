package br.com.feedflow.reports.publishing;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnsReportPublisherTest {

    @Test
    void devePublicarNoTopicoDeRelatoriosConfigurado() {
        AtomicReference<PublishRequest> capturedRequest = new AtomicReference<>();
        SnsClient client = snsClient(request -> {
            capturedRequest.set(request);
            return PublishResponse.builder().messageId("message-1").build();
        });
        SnsReportPublisher publisher = new SnsReportPublisher(
                client,
                "arn:aws:sns:us-east-2:123456789012:feedflow-reports");

        publisher.publish("relatório semanal");

        assertEquals(
                "arn:aws:sns:us-east-2:123456789012:feedflow-reports",
                capturedRequest.get().topicArn());
        assertEquals("relatório semanal", capturedRequest.get().message());
        assertEquals("[FeedFlow] Relatorio semanal", capturedRequest.get().subject());
    }

    @Test
    void deveFalharAInvocacaoQuandoAPublicacaoSnsFalhar() {
        SnsClient client = snsClient(request -> {
            throw SnsException.builder().message("falha simulada").build();
        });
        SnsReportPublisher publisher = new SnsReportPublisher(
                client,
                "arn:aws:sns:us-east-2:123456789012:feedflow-reports");

        assertThrows(ReportPublicationException.class, () -> publisher.publish("relatório semanal"));
    }

    @Test
    void deveRejeitarRelatorioQueExcedaLimiteSnsEmUtf8SemPublicar() {
        AtomicInteger publications = new AtomicInteger();
        SnsClient client = snsClient(request -> {
            publications.incrementAndGet();
            return PublishResponse.builder().messageId("unexpected").build();
        });
        SnsReportPublisher publisher = new SnsReportPublisher(
                client,
                "arn:aws:sns:us-east-2:123456789012:feedflow-reports");
        String oversizedReport = "á".repeat((SnsReportPublisher.MAX_MESSAGE_BYTES / 2) + 1);

        ReportPublicationException exception = assertThrows(
                ReportPublicationException.class,
                () -> publisher.publish(oversizedReport));

        assertEquals(SnsReportPublisher.MAX_MESSAGE_BYTES + 2,
                oversizedReport.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(0, publications.get());
        assertEquals(
                "O relatório semanal excede o limite SNS de 262144 bytes UTF-8: 262146 bytes.",
                exception.getMessage());
    }

    private SnsClient snsClient(PublishOperation publishOperation) {
        return (SnsClient) Proxy.newProxyInstance(
                SnsClient.class.getClassLoader(),
                new Class<?>[]{SnsClient.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("publish")
                            && arguments != null
                            && arguments.length == 1
                            && arguments[0] instanceof PublishRequest request) {
                        return publishOperation.execute(request);
                    }
                    if (method.getName().equals("serviceName")) {
                        return "sns";
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    throw new UnsupportedOperationException("Operação não simulada: " + method.getName());
                });
    }

    @FunctionalInterface
    private interface PublishOperation {
        PublishResponse execute(PublishRequest request);
    }
}
