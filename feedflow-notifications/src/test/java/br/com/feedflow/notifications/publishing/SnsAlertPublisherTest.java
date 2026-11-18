package br.com.feedflow.notifications.publishing;

import br.com.feedflow.notifications.domain.CriticalFeedback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnsAlertPublisherTest {

    private static final String TOPIC_ARN =
            "arn:aws:sns:us-east-2:123456789012:feedflow-dev-critical-alerts";

    @Mock
    SnsClient snsClient;

    @Test
    void deveEnviarAoTopicoMensagemComTodosOsCamposObrigatorios() throws Exception {
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("message-1").build());
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T13:00:00Z"), ZoneOffset.UTC);
        SnsAlertPublisher publisher = new SnsAlertPublisher(
                snsClient,
                TOPIC_ARN,
                new AlertMessageFormatter(clock));
        CriticalFeedback feedback = new CriticalFeedback(
                UUID.fromString("28f3a1c9-df41-41be-bd78-fe03f0312fd7"),
                1,
                "CRITICA",
                "Professor não conseguiu abrir o material.",
                Instant.parse("2026-11-02T12:30:00Z"));

        publisher.publish(feedback);

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        PublishRequest request = captor.getValue();
        assertEquals(TOPIC_ARN, request.topicArn());
        assertEquals(SnsAlertPublisher.SUBJECT, request.subject());
        assertTrue(request.message().contains("28f3a1c9-df41-41be-bd78-fe03f0312fd7"));
        assertTrue(request.message().contains("Nota: 1"));
        assertTrue(request.message().contains("Urgência: CRITICA"));
        assertTrue(request.message().contains("Professor não conseguiu abrir o material."));
        assertTrue(request.message().contains("Criada em (UTC): 2026-11-02T12:30:00Z"));
        assertTrue(request.message().contains("Alerta gerado em (UTC): 2026-11-02T13:00:00Z"));
    }

    @Test
    void deveTransformarFalhaDoSdkEmFalhaTecnicaDaAplicacao() {
        when(snsClient.publish(any(PublishRequest.class)))
                .thenThrow(SnsException.builder().message("falha simulada").build());
        SnsAlertPublisher publisher = new SnsAlertPublisher(
                snsClient,
                TOPIC_ARN,
                new AlertMessageFormatter());
        CriticalFeedback feedback = new CriticalFeedback(
                UUID.fromString("28f3a1c9-df41-41be-bd78-fe03f0312fd7"),
                0,
                "CRITICA",
                "Falha crítica.",
                Instant.parse("2026-11-02T12:30:00Z"));

        assertThrows(AlertPublicationException.class, () -> publisher.publish(feedback));
    }
}
