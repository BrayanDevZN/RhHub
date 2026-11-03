package br.rhhub.infra.messaging;

import br.rhhub.application.port.out.DeadLetterRepository;
import br.rhhub.domain.model.DeadLetter;
import java.time.Duration;
import java.util.function.BiFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

/**
 * Último passo de um job que esgotou as tentativas: grava o resultado na tabela dead_letters e publica a mensagem
 * original no tópico {@code <nome>.DLT}. Se qualquer um dos dois falhar, a exceção sobe e o Kafka entrega a
 * mensagem de novo (gravar duas vezes não duplica: a origem é única), então o job nunca se perde.
 */
class DeadLetterRecoverer implements ConsumerRecordRecoverer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRecoverer.class);

    private final DeadLetterRepository deadLetters;
    private final DeadLetterPublishingRecoverer toTopic;
    private final BiFunction<ConsumerRecord<?, ?>, Exception, Integer> attempts;

    DeadLetterRecoverer(DeadLetterRepository deadLetters, DeadLetterPublishingRecoverer toTopic,
            BiFunction<ConsumerRecord<?, ?>, Exception, Integer> attempts) {
        this.deadLetters = deadLetters;
        this.toTopic = toTopic;
        this.attempts = attempts;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        Throwable cause = exception instanceof ListenerExecutionFailedException && exception.getCause() != null
                ? exception.getCause() : exception;
        var failure = DeadLetter.failed(record.topic(), record.key() == null ? null : record.key().toString(),
                record.partition(), record.offset(), String.valueOf(record.value()), cause,
                attempts.apply(record, exception));
        var saved = deadLetters.save(failure).block(Duration.ofSeconds(30));
        toTopic.accept(record, exception);
        log.error("Job {}[{}@{}] foi para a DLQ (id {}): {}", record.topic(), record.partition(), record.offset(),
                saved == null ? null : saved.id(), cause.toString());
    }
}
