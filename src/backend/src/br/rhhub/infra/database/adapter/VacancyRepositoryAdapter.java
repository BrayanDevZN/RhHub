package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.infra.database.entity.VacancyEntity;
import br.rhhub.infra.database.jpa.VacancyJpaRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class VacancyRepositoryAdapter implements VacancyRepository {

    private final VacancyJpaRepository jpa;
    private final Clock clock;

    public VacancyRepositoryAdapter(VacancyJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vacancy> findById(Long id) {
        return jpa.findById(id).map(VacancyRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vacancy> findAll() {
        return jpa.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(VacancyRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return jpa.existsByName(name);
    }

    @Override
    public Vacancy save(Vacancy vacancy) {
        var entity = new VacancyEntity(
                vacancy.id(),
                vacancy.createdBy(),
                vacancy.name(),
                vacancy.description(),
                vacancy.createdAt() != null ? vacancy.createdAt() : clock.instant());
        try {
            return toDomain(jpa.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throw new DomainException.Conflict("Já existe uma vaga com esse nome", e);
        }
    }

    @Override
    public void deleteById(Long id) {
        if (jpa.existsById(id)) {
            jpa.deleteById(id);
        }
    }

    static Vacancy toDomain(VacancyEntity e) {
        return new Vacancy(e.getId(), e.getName(), e.getDescription(), e.getCreatedBy(), e.getCreatedAt());
    }
}
