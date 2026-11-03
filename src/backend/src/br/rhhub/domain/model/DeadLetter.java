package br.rhhub.domain.model;

import java.time.Instant;

/**
 * Resultado de um job que falhou de vez. {@code key} é a chave da mensagem (o id da candidatura);
 * {@code partition} e {@code offset} dizem de onde ela veio no Kafka e são nulos se não veio de lá.
 * {@code id}, {@code createdAt} e {@code resolvedAt} são nulos antes de gravar ou enquanto não resolvido.
 */
public record DeadLetter(
        Long id,
        String topic,
        String key,
        Integer partition,
        Long offset,
        String payload,
        String errorType,
        String errorMessage,
        int attempts,
        DeadLetterStatus status,
        Instant createdAt,
        Instant resolvedAt) {

    /** Uma falha nova, ainda não gravada. */
    public static DeadLetter failed(String topic, String key, Integer partition, Long offset, String payload,
            Throwable error, int attempts) {
        return new DeadLetter(null, topic, key, partition, offset, payload, error.getClass().getName(),
                error.getMessage(), attempts, DeadLetterStatus.FAILED, null, null);
    }
}
