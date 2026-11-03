package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeFile;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.infra.database.entity.ResumeEntity;
import br.rhhub.infra.database.jpa.ResumeJpaRepository;
import br.rhhub.infra.database.jpa.ResumeView;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class ResumeRepositoryAdapter implements ResumeRepository {

    private final ResumeJpaRepository jpa;
    private final Clock clock;

    public ResumeRepositoryAdapter(ResumeJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Resume> findById(Long id) {
        return jpa.findViewById(id).map(ResumeRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Resume> findByUserId(Long userId) {
        return jpa.findViewsByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ResumeRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Resume> findAll(ResumeStatus status, Long vacancyId) {
        return jpa.search(status != null ? status.value() : null, vacancyId).stream()
                .map(ResumeRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumeFile> findFile(Long id) {
        return jpa.findFileViewById(id)
                .map(v -> new ResumeFile(v.getFile(), v.getFileName(), v.getContentType()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserIdAndVacancyId(Long userId, Long vacancyId) {
        return jpa.existsByUserIdAndVacancyId(userId, vacancyId);
    }

    @Override
    public Resume create(Resume resume, byte[] file) {
        var entity = new ResumeEntity(
                resume.userId(),
                resume.vacancyId(),
                file,
                resume.fileName(),
                resume.contentType(),
                resume.status().value(),
                resume.reason(),
                resume.createdAt() != null ? resume.createdAt() : clock.instant());
        try {
            var saved = jpa.saveAndFlush(entity);
            return new Resume(saved.getId(), saved.getUserId(), saved.getVacancyId(), saved.getFileName(),
                    saved.getContentType(), ResumeStatus.from(saved.getStatus()), saved.getReason(),
                    saved.getCreatedAt());
        } catch (DataIntegrityViolationException e) {
            throw new DomainException.Conflict("Você já se candidatou a esta vaga", e);
        }
    }

    @Override
    public Resume updateResult(Long id, ResumeStatus status, String reason) {
        if (jpa.updateResult(id, status.value(), reason) == 0) {
            throw new DomainException.NotFound("Candidatura não encontrada");
        }
        return findById(id).orElseThrow(() -> new DomainException.NotFound("Candidatura não encontrada"));
    }

    @Override
    public void deleteById(Long id) {
        if (jpa.existsById(id)) {
            jpa.deleteById(id);
        }
    }

    static Resume toDomain(ResumeView v) {
        return new Resume(v.getId(), v.getUserId(), v.getVacancyId(), v.getFileName(), v.getContentType(),
                ResumeStatus.from(v.getStatus()), v.getReason(), v.getCreatedAt());
    }
}
