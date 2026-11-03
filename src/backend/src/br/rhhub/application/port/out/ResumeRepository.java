package br.rhhub.application.port.out;

import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeFile;
import br.rhhub.domain.model.ResumeStatus;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository {

    Optional<Resume> findById(Long id);

    List<Resume> findByUserId(Long userId);

    /** Filtros opcionais: passe null para ignorar. */
    List<Resume> findAll(ResumeStatus status, Long vacancyId);

    Optional<ResumeFile> findFile(Long id);

    boolean existsByUserIdAndVacancyId(Long userId, Long vacancyId);

    /** Grava a candidatura nova com o arquivo. Lança DomainException.Conflict se o usuário já se candidatou à vaga. */
    Resume create(Resume resume, byte[] file);

    /** Atualiza só status e reason (resultado da análise). Lança DomainException.NotFound se não existir. */
    Resume updateResult(Long id, ResumeStatus status, String reason);

    void deleteById(Long id);
}
