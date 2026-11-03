package br.rhhub.infra.database.jpa;

import br.rhhub.infra.database.entity.VacancyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacancyJpaRepository extends JpaRepository<VacancyEntity, Long> {

    boolean existsByName(String name);
}
