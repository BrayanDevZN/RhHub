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

    /**
     * Cria o consumidor da fila de e-mails.
     */
    public EmailConsumer(DeliverEmail deliverEmail, ObjectMapper mapper) {
        this.deliverEmail = deliverEmail;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = "#{@kafkaTopics.email()}",
            groupId = "#{@kafkaTopics.emailGroup()}",
            concurrency = "#{@kafkaTopics.partitions()}")
    /**
     * Recebe uma mensagem do tópico email.send, converte o JSON em e-mail e entrega ao envio (n8n), esperando terminar. Se falhar, o Kafka tenta de novo e, esgotadas as tentativas, vai para a dead letter.
     */
    public void onMessage(String payload) throws JsonProcessingException {
        deliverEmail.execute(mapper.readValue(payload, EmailKafkaMessage.class).toEmail()).block();
    }
}
