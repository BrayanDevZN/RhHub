package br.rhhub.domain.model;

import java.time.Instant;

/** Aviso dentro do app (o sino). link é a rota do front para onde o clique leva; pode ser nulo. */
public record Notification(
        Long id,
        Long userId,
        String title,
        String message,
        String link,
        boolean read,
        Instant createdAt) {
}
