package br.rhhub.application.port.out;

import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeFile;
import br.rhhub.domain.model.ResumeStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ResumeRepository {

    Mono<Resume> findById(Long id);

    Flux<Resume> findByUserId(Long userId);

    /** Filtros opcionais: passe null para ignorar. */
    Flux<Resume> findAll(ResumeStatus status, Long vacancyId);

    Mono<ResumeFile> findFile(Long id);

    Mono<Boolean> existsByUserIdAndVacancyId(Long userId, Long vacancyId);

    /** Grava a candidatura nova com o arquivo. Erro DomainException.Conflict se o usuário já se candidatou à vaga. */
    Mono<Resume> create(Resume resume, byte[] file);

    /** Atualiza só status e reason. Erro DomainException.NotFound se a candidatura não existir. */
    Mono<Resume> updateResult(Long id, ResumeStatus status, String reason);

    /** Muda só a etapa do processo seletivo. Erro DomainException.NotFound se a candidatura não existir. */
    Mono<Resume> updateStage(Long id, String stage);

    Mono<Void> deleteById(Long id);
}
