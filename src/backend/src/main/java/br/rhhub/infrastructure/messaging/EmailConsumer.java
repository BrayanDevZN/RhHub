package br.rhhub.infrastructure.messaging;

import br.rhhub.application.port.in.DeliverEmail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Lê o topic email.send. Se o envio falhar, a exceção sobe para o error handler do Kafka
 * (novas tentativas e, depois, topic .DLT).
 */
@Component
public class EmailConsumer {

    private final DeliverEmail deliverEmail;
    private final ObjectMapper mapper;

    public EmailConsumer(DeliverEmail deliverEmail, ObjectMapper mapper) {
        this.deliverEmail = deliverEmail;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${app.topics.email}", groupId = "${app.topics.consumer-group}")
    public void onMessage(String payload) throws JsonProcessingException {
        deliverEmail.execute(mapper.readValue(payload, EmailKafkaMessage.class).toDomain());
    }
}
