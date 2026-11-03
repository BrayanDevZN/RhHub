package br.rhhub.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.rhhub.application.port.in.DeliverEmail;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.infrastructure.TestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class MessagingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    @Test
    void emailPublisherKeysByRecipientAndRoundTripsThroughConsumer() throws Exception {
        var message = new EmailMessage("ana@gmail.com", "Oi", "<p>x</p>");
        new KafkaEmailPublisher(kafka, mapper, TestSupport.props()).publish(message);

        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(org.mockito.ArgumentMatchers.eq("email.send"),
                org.mockito.ArgumentMatchers.eq("ana@gmail.com"), payload.capture());

        var deliver = mock(DeliverEmail.class);
        new EmailConsumer(deliver, mapper).onMessage(payload.getValue());
        verify(deliver).execute(message);
    }

    @Test
    void analysisPublisherSendsOnlyTheId() {
        new KafkaResumeAnalysisPublisher(kafka, mapper, TestSupport.props()).publish(42L);

        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(org.mockito.ArgumentMatchers.eq("resume.analysis"),
                org.mockito.ArgumentMatchers.eq("42"), payload.capture());
        assertThat(payload.getValue()).isEqualTo("{\"resumeId\":42}");
    }
}
