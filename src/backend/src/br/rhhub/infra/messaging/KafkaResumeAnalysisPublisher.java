package br.rhhub.infra.messaging;

import br.rhhub.application.port.out.ResumeAnalysisPublisher;
import br.rhhub.infra.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class KafkaResumeAnalysisPublisher implements ResumeAnalysisPublisher {

    private final KafkaPublisher kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public KafkaResumeAnalysisPublisher(KafkaPublisher kafka, ObjectMapper mapper, AppProperties props) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = props.kafka().analysisTopic();
    }

    @Override
    public Mono<Void> publish(Long resumeId) {
        try {
            return kafka.send(topic, String.valueOf(resumeId), mapper.writeValueAsString(new ResumeAnalysisMessage(resumeId)));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Falha ao serializar a mensagem de análise", e));
        }
    }
}
