package br.rhhub.infrastructure.persistence.jpa;

import br.rhhub.infrastructure.persistence.entity.VacancyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacancyJpaRepository extends JpaRepository<VacancyEntity, Long> {

    boolean existsByName(String name);
}
