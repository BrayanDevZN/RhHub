package br.rhhub.service;

import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.domain.rule.Rules;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Qualquer usuário logado vê as vagas; só admin cria, edita e exclui. */
public class VacancyService {

    private final VacancyRepository vacancies;
    private final AccessPolicy policy;

    public VacancyService(VacancyRepository vacancies, AccessPolicy policy) {
        this.vacancies = vacancies;
        this.policy = policy;
    }

    public Flux<Vacancy> list() {
        return vacancies.findAll();
    }

    public Mono<Vacancy> get(Long id) {
        return vacancies.findById(id).switchIfEmpty(Mono.error(notFound()));
    }

    public Mono<Vacancy> create(User actor, String rawName, String rawDescription) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            String name = Rules.vacancyName(rawName);
            String description = Rules.vacancyDescription(rawDescription);
            return vacancies.existsByName(name).flatMap(exists -> exists
                    ? Mono.<Vacancy>error(nameTaken())
                    : vacancies.save(new Vacancy(null, name, description, actor.id(), null)));
        });
    }

    /** Campos nulos ficam como estão. */
    public Mono<Vacancy> update(User actor, Long id, String rawName, String rawDescription) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            String name = rawName == null ? null : Rules.vacancyName(rawName);
            String description = rawDescription == null ? null : Rules.vacancyDescription(rawDescription);
            return get(id).flatMap(current -> {
                String newName = name == null ? current.name() : name;
                var updated = new Vacancy(current.id(), newName, description == null ? current.description() : description,
                        current.createdBy(), current.createdAt());
                if (newName.equals(current.name())) {
                    return vacancies.save(updated);
                }
                return vacancies.existsByName(newName).flatMap(exists -> exists
                        ? Mono.<Vacancy>error(nameTaken())
                        : vacancies.save(updated));
            });
        });
    }

    /** Apaga também as candidaturas da vaga (cascata no banco). */
    public Mono<Void> delete(User actor, Long id) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            return get(id).then(Mono.defer(() -> vacancies.deleteById(id)));
        });
    }

    private static DomainException.NotFound notFound() {
        return new DomainException.NotFound("Vaga não encontrada.");
    }

    private static DomainException.Conflict nameTaken() {
        return new DomainException.Conflict("Já existe uma vaga com esse nome.");
    }
}
