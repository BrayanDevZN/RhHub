package br.rhhub.domain.model;

import java.time.Instant;

/** O conteúdo do arquivo não viaja neste modelo: use ResumeRepository.findFile. */
public record Resume(
        Long id,
        Long userId,
        Long vacancyId,
        String fileName,
        String contentType,
        ResumeStatus status,
        String reason,
        Instant createdAt) {
}
