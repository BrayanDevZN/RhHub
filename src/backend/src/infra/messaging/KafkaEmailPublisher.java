package br.rhhub.infra.messaging;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.infra.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Coloca um e-mail na fila do Kafka (tópico de e-mails); quem envia de fato é o EmailConsumer. */
@Component
public class KafkaEmailPublisher implements EmailPublisher {

    private final KafkaPublisher kafka;
    private final ObjectMapper mapper;
    private final String topic;

    /**
     * Cria o publicador de e-mails, com o nome do tópico vindo da configuração.
     */
    public KafkaEmailPublisher(KafkaPublisher kafka, ObjectMapper mapper, AppProperties props) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = props.kafka().emailTopic();
    }

    /**
     * Coloca um e-mail na fila email.send, em JSON. A chave é o id da candidatura. Falha ao montar o JSON ou ao publicar sobe como erro para quem chamou.
     */
    @Override
    public Mono<Void> publish(Long resumeId, EmailMessage message) {
        try {
            return kafka.send(topic, String.valueOf(resumeId), mapper.writeValueAsString(EmailKafkaMessage.of(resumeId, message)));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Falha ao serializar o e-mail", e));
        }
    }
}
