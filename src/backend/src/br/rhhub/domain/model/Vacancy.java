package br.rhhub.domain.model;

import java.time.Instant;

/** createdBy fica nulo se o admin que criou a vaga for excluído. */
public record Vacancy(Long id, String name, String description, Long createdBy, Instant createdAt) {
}
