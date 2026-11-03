package br.rhhub.infrastructure.messaging;

import br.rhhub.application.port.out.ResumeAnalysisPublisher;
import br.rhhub.infrastructure.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaResumeAnalysisPublisher implements ResumeAnalysisPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public KafkaResumeAnalysisPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper, AppProperties props) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = props.topics().resumeAnalysis();
    }

    @Override
    public void publish(Long resumeId) {
        try {
            kafka.send(topic, String.valueOf(resumeId), mapper.writeValueAsString(new ResumeAnalysisMessage(resumeId)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar a mensagem de análise", e);
        }
    }
}
