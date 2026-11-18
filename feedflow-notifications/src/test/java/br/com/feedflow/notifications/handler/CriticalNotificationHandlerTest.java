package br.com.feedflow.notifications.handler;

import br.com.feedflow.notifications.domain.CriticalFeedback;
import br.com.feedflow.notifications.processing.CriticalFeedbackMapper;
import br.com.feedflow.notifications.processing.CriticalNotificationProcessor;
import br.com.feedflow.notifications.processing.CriticalRecordSelector;
import br.com.feedflow.notifications.publishing.AlertPublicationException;
import br.com.feedflow.notifications.publishing.AlertPublisher;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CriticalNotificationHandlerTest {

    @Mock
    AlertPublisher publisher;

    CriticalNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CriticalNotificationHandler(new CriticalNotificationProcessor(
                new CriticalRecordSelector(),
                new CriticalFeedbackMapper(),
                publisher));
    }

    @Test
    void devePublicarEventoInsertCriticoComTodosOsDados() throws Exception {
        StreamsEventResponse response = handler.handleRequest(
                event(record("INSERT", "CRITICA", "100")),
                null);

        assertTrue(response.getBatchItemFailures().isEmpty());
        ArgumentCaptor<CriticalFeedback> captor = ArgumentCaptor.forClass(CriticalFeedback.class);
        verify(publisher).publish(captor.capture());
        CriticalFeedback feedback = captor.getValue();
        assertEquals("28f3a1c9-df41-41be-bd78-fe03f0312fd7", feedback.id().toString());
        assertEquals(2, feedback.nota());
        assertEquals("CRITICA", feedback.urgencia());
        assertEquals("Conteúdo não carregou durante a aula.", feedback.descricao());
        assertEquals("2026-11-02T12:30:00Z", feedback.criadoEm().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEDIA", "BAIXA"})
    void deveIgnorarEventosInsertNaoCriticos(String urgency) throws Exception {
        StreamsEventResponse response = handler.handleRequest(
                event(record("INSERT", urgency, "200")),
                null);

        assertTrue(response.getBatchItemFailures().isEmpty());
        verify(publisher, never()).publish(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MODIFY", "REMOVE"})
    void deveIgnorarEventosQueNaoSejamInsert(String eventName) throws Exception {
        StreamsEventResponse response = handler.handleRequest(
                event(record(eventName, "CRITICA", "300")),
                null);

        assertTrue(response.getBatchItemFailures().isEmpty());
        verify(publisher, never()).publish(any());
    }

    @Test
    void deveDescartarInsertSemNewImageSemInterromperOLote() throws Exception {
        DynamodbStreamRecord missingImage = record("INSERT", "CRITICA", "400");
        missingImage.getDynamodb().setNewImage(null);

        StreamsEventResponse response = handler.handleRequest(
                event(missingImage, record("INSERT", "MEDIA", "401")),
                null);

        assertTrue(response.getBatchItemFailures().isEmpty());
        verify(publisher, never()).publish(any());
    }

    @Test
    void deveDescartarCamposInvalidosEContinuarProcessandoOutrosRegistros() throws Exception {
        DynamodbStreamRecord missingId = record("INSERT", "CRITICA", "500");
        missingId.getDynamodb().getNewImage().remove("id");
        DynamodbStreamRecord invalidScore = record("INSERT", "CRITICA", "501");
        invalidScore.getDynamodb().getNewImage().put("nota", number("2.5"));
        DynamodbStreamRecord invalidDate = record("INSERT", "CRITICA", "502");
        invalidDate.getDynamodb().getNewImage().put("createdAt", text("data-invalida"));

        StreamsEventResponse response = handler.handleRequest(
                event(missingId, invalidScore, invalidDate, record("INSERT", "MEDIA", "503")),
                null);

        assertTrue(response.getBatchItemFailures().isEmpty());
        verify(publisher, never()).publish(any());
    }

    @Test
    void deveRetornarFalhaParcialDoSnsEContinuarOLote() throws Exception {
        doThrow(new AlertPublicationException("falha simulada", new IllegalStateException()))
                .doNothing()
                .when(publisher)
                .publish(any());

        StreamsEventResponse response = handler.handleRequest(
                event(
                        record("INSERT", "CRITICA", "600"),
                        record("INSERT", "CRITICA", "601")),
                null);

        assertEquals(1, response.getBatchItemFailures().size());
        assertEquals("600", response.getBatchItemFailures().get(0).getItemIdentifier());
        verify(publisher, times(2)).publish(any());
    }

    @Test
    void deveProcessarLoteComMultiplosTiposDeRegistro() throws Exception {
        StreamsEventResponse response = handler.handleRequest(
                event(
                        record("INSERT", "CRITICA", "700"),
                        record("INSERT", "MEDIA", "701"),
                        record("MODIFY", "CRITICA", "702"),
                        record("INSERT", "CRITICA", "703"),
                        record("INSERT", "BAIXA", "704")),
                null);

        assertTrue(response.getBatchItemFailures().isEmpty());
        verify(publisher, times(2)).publish(any());
    }

    @Test
    void deveRepublicarEventoDuplicadoEmNovoProcessamento() throws Exception {
        DynamodbEvent duplicatedEvent = event(record("INSERT", "CRITICA", "800"));

        StreamsEventResponse firstResponse = handler.handleRequest(duplicatedEvent, null);
        StreamsEventResponse secondResponse = handler.handleRequest(duplicatedEvent, null);

        assertTrue(firstResponse.getBatchItemFailures().isEmpty());
        assertTrue(secondResponse.getBatchItemFailures().isEmpty());
        verify(publisher, times(2)).publish(any());
    }

    private static DynamodbEvent event(DynamodbStreamRecord... records) {
        DynamodbEvent event = new DynamodbEvent();
        event.setRecords(List.of(records));
        return event;
    }

    private static DynamodbStreamRecord record(
            String eventName,
            String urgency,
            String sequenceNumber) {
        Map<String, AttributeValue> image = new HashMap<>();
        image.put("id", text("28f3a1c9-df41-41be-bd78-fe03f0312fd7"));
        image.put("nota", number("2"));
        image.put("urgencia", text(urgency));
        image.put("descricao", text("Conteúdo não carregou durante a aula."));
        image.put("createdAt", text("2026-11-02T12:30:00Z"));

        StreamRecord streamRecord = new StreamRecord();
        streamRecord.setSequenceNumber(sequenceNumber);
        streamRecord.setNewImage(image);

        DynamodbStreamRecord record = new DynamodbStreamRecord();
        record.setEventID("event-" + sequenceNumber);
        record.setEventName(eventName);
        record.setDynamodb(streamRecord);
        return record;
    }

    private static AttributeValue text(String value) {
        return new AttributeValue().withS(value);
    }

    private static AttributeValue number(String value) {
        return new AttributeValue().withN(value);
    }
}
