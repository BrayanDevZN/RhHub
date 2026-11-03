package br.rhhub.infra.messaging;

import br.rhhub.application.port.in.DeliverEmail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Lê o tópico de e-mail (um consumer por partição) e entrega a mensagem ao caso de uso. */
@Component
public class EmailConsumer {

    private final DeliverEmail deliverEmail;
    private final ObjectMapper mapper;

    public EmailConsumer(DeliverEmail deliverEmail, ObjectMapper mapper) {
        this.deliverEmail = deliverEmail;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = "#{@kafkaTopics.email()}",
            groupId = "#{@kafkaTopics.emailGroup()}",
            concurrency = "#{@kafkaTopics.partitions()}")
    public void onMessage(String payload) throws JsonProcessingException {
        deliverEmail.execute(mapper.readValue(payload, EmailKafkaMessage.class).toEmail()).block();
    }
}
