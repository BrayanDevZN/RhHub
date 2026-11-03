package br.rhhub.domain.model;

import br.rhhub.domain.rule.Stages;
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
        Instant createdAt,
        String stage) {

    /** Etapa do processo seletivo (uma das etapas da vaga). Nasce na Triagem. */
    public Resume {
        stage = stage == null || stage.isBlank() ? Stages.SCREENING : stage;
    }

    public Resume(Long id, Long userId, Long vacancyId, String fileName, String contentType, ResumeStatus status,
            String reason, Instant createdAt) {
        this(id, userId, vacancyId, fileName, contentType, status, reason, createdAt, null);
    }
}
