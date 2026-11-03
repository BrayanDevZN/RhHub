package br.rhhub.domain.model;

import java.time.Instant;

/**
 * Só a descrição é obrigatória; requisitos e responsabilidades são opcionais (nulos quando vazios). createdBy fica nulo
 * se o admin que criou a vaga for excluído. hasPhoto diz se a vaga tem foto (a imagem em si fica em VacancyPhotoRepository).
 */
public record Vacancy(
        Long id,
        String name,
        String description,
        String requirements,
        String responsibilities,
        Long createdBy,
        Instant createdAt,
        boolean hasPhoto) {

    /** Vaga só com o básico, sem requisitos, responsabilidades nem foto. */
    public Vacancy(Long id, String name, String description, Long createdBy, Instant createdAt) {
        this(id, name, description, null, null, createdBy, createdAt, false);
    }

    public Vacancy withHasPhoto(boolean value) {
        return new Vacancy(id, name, description, requirements, responsibilities, createdBy, createdAt, value);
    }
}
