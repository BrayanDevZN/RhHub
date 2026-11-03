package br.rhhub.infrastructure.messaging;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.infrastructure.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** A chave é o destinatário: e-mails do mesmo usuário ficam na mesma partição, em ordem. */
@Component
public class KafkaEmailPublisher implements EmailPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public KafkaEmailPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper, AppProperties props) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = props.topics().email();
    }

    @Override
    public void publish(EmailMessage message) {
        try {
            kafka.send(topic, message.to(), mapper.writeValueAsString(EmailKafkaMessage.from(message)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o e-mail", e);
        }
    }
}
