package br.rhhub.domain.model;

import java.time.Instant;

public record Vacancy(Long id, String name, String description, Long createdBy, Instant createdAt) {

    public Vacancy withNameAndDescription(String newName, String newDescription) {
        return new Vacancy(id, newName, newDescription, createdBy, createdAt);
    }
}
