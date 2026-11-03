package br.rhhub.infra.messaging;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.infra.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class KafkaEmailPublisher implements EmailPublisher {

    private final KafkaPublisher kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public KafkaEmailPublisher(KafkaPublisher kafka, ObjectMapper mapper, AppProperties props) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = props.kafka().emailTopic();
    }

    @Override
    public Mono<Void> publish(Long resumeId, EmailMessage message) {
        try {
            return kafka.send(topic, String.valueOf(resumeId), mapper.writeValueAsString(EmailKafkaMessage.of(resumeId, message)));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Falha ao serializar o e-mail", e));
        }
    }
}
