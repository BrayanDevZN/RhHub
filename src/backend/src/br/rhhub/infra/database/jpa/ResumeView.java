package br.rhhub.infra.database.jpa;

import java.time.Instant;

/** Projeção fechada: o Spring Data seleciona só estas colunas, sem o arquivo. */
public interface ResumeView {

    Long getId();

    Long getUserId();

    Long getVacancyId();

    String getFileName();

    String getContentType();

    String getStatus();

    String getReason();

    Instant getCreatedAt();
}
