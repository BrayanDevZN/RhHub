package br.rhhub.infra.messaging;

import br.rhhub.application.port.out.ResumeAnalysisPublisher;
import br.rhhub.infra.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Coloca a candidatura na fila do Kafka para a IA analisar; o AnalysisConsumer processa. */
@Component
public class KafkaResumeAnalysisPublisher implements ResumeAnalysisPublisher {

    private final KafkaPublisher kafka;
    private final ObjectMapper mapper;
    private final String topic;

    /**
     * Cria o publicador de pedidos de análise, com o nome do tópico vindo da configuração.
     */
    public KafkaResumeAnalysisPublisher(KafkaPublisher kafka, ObjectMapper mapper, AppProperties props) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = props.kafka().analysisTopic();
    }

    /**
     * Coloca o pedido de análise de uma candidatura na fila resume.analysis. A mensagem leva só o id; o consumer busca o resto no banco, então ela é pequena e nunca fica velha.
     */
    @Override
    public Mono<Void> publish(Long resumeId) {
        try {
            return kafka.send(topic, String.valueOf(resumeId), mapper.writeValueAsString(new ResumeAnalysisMessage(resumeId)));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Falha ao serializar a mensagem de análise", e));
        }
    }
}
