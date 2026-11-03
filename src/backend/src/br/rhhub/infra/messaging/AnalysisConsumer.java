package br.rhhub.infra.messaging;

import br.rhhub.application.port.in.AnalyzeResume;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Lê o tópico de análise (um consumer por partição) e entrega o id ao caso de uso. Se o caso de uso falhar, a
 * exceção sobe para o error handler: novas tentativas e, no fim, DLT e a tabela dead_letters.
 */
@Component
public class AnalysisConsumer {

    private final ObjectProvider<AnalyzeResume> analyze;
    private final ObjectMapper mapper;

    public AnalysisConsumer(ObjectProvider<AnalyzeResume> analyze, ObjectMapper mapper) {
        this.analyze = analyze;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = "#{@kafkaTopics.resumeAnalysis()}",
            groupId = "#{@kafkaTopics.analysisGroup()}",
            concurrency = "#{@kafkaTopics.partitions()}")
    public void onMessage(String payload) throws JsonProcessingException {
        var message = mapper.readValue(payload, ResumeAnalysisMessage.class);
        if (message.resumeId() == null) {
            throw new IllegalArgumentException("Mensagem de análise sem resumeId");
        }
        AnalyzeResume useCase = analyze.getIfAvailable();
        if (useCase == null) {
            throw new IllegalStateException("O caso de uso de análise ainda não foi implementado");
        }
        useCase.execute(message.resumeId()).block();
    }
}
