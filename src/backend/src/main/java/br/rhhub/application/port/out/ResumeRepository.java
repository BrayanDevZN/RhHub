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

    /** Grava a candidatura nova com o arquivo, ou atualiza status e reason de uma existente. */
    Resume create(Resume resume, byte[] file);

    Resume update(Resume resume);

    void deleteById(Long id);
}
