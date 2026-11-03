package br.rhhub.service;

import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.application.port.out.VacancyPhotoRepository;
import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.ProfilePhoto;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.domain.rule.Rules;
import br.rhhub.domain.rule.Stages;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Qualquer usuário logado vê as vagas e as fotos; só admin cria, edita e exclui. Só a descrição é obrigatória:
 * requisitos e responsabilidades são opcionais.
 */
public class VacancyService {

    private final VacancyRepository vacancies;
    private final VacancyPhotoRepository photos;
    private final AccessPolicy policy;
    private final ResumeRepository resumes;

    private final Clock clock;

    public VacancyService(VacancyRepository vacancies, VacancyPhotoRepository photos, AccessPolicy policy,
            ResumeRepository resumes) {
        this(vacancies, photos, policy, resumes, Clock.systemUTC());
    }

    public VacancyService(VacancyRepository vacancies, VacancyPhotoRepository photos, AccessPolicy policy,
            ResumeRepository resumes, Clock clock) {
        this.clock = clock;
        this.vacancies = vacancies;
        this.photos = photos;
        this.policy = policy;
        this.resumes = resumes;
    }

    public Flux<Vacancy> list() {
        return vacancies.findAll();
    }

    public Mono<Vacancy> get(Long id) {
        return vacancies.findById(id).switchIfEmpty(Mono.error(notFound()));
    }

    public Mono<Vacancy> create(User actor, String rawName, String rawDescription, String rawRequirements,
            String rawResponsibilities) {
        return create(actor, rawName, rawDescription, rawRequirements, rawResponsibilities, null);
    }

    /** rawStages nulo ou vazio: etapas padrão. A Triagem entra sempre, como primeira etapa. */
    public Mono<Vacancy> create(User actor, String rawName, String rawDescription, String rawRequirements,
            String rawResponsibilities, List<String> rawStages) {
        return create(actor, rawName, rawDescription, rawRequirements, rawResponsibilities, rawStages, null);
    }

    /** rawExpiresAt é opcional: data e hora ISO-8601 (ex.: 2026-12-31T23:59:59Z), no futuro. Vazio: sem prazo. */
    public Mono<Vacancy> create(User actor, String rawName, String rawDescription, String rawRequirements,
            String rawResponsibilities, List<String> rawStages, String rawExpiresAt) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            String name = Rules.vacancyName(rawName);
            String description = Rules.vacancyDescription(rawDescription);
            String requirements = Rules.optionalText(rawRequirements, "Os requisitos podem ter");
            String responsibilities = Rules.optionalText(rawResponsibilities, "As responsabilidades podem ter");
            List<String> stages = Stages.normalize(rawStages);
            Instant expiresAt = parseExpiration(rawExpiresAt);
            return vacancies.existsByName(name).flatMap(exists -> exists
                    ? Mono.<Vacancy>error(nameTaken())
                    : vacancies.save(new Vacancy(null, name, description, requirements, responsibilities, actor.id(), null,
                            false, stages, expiresAt, 0)));
        });
    }

    /**
     * Campos nulos ficam como estão. Em requisitos e responsabilidades, texto vazio apaga o campo; a descrição não pode
     * ficar vazia.
     */
    public Mono<Vacancy> update(User actor, Long id, String rawName, String rawDescription, String rawRequirements,
            String rawResponsibilities) {
        return update(actor, id, rawName, rawDescription, rawRequirements, rawResponsibilities, null);
    }

    /**
     * rawStages nulo mantém as etapas atuais. Etapa que já tem candidatos não pode sair da vaga, senão eles ficariam
     * numa etapa que não existe mais.
     */
    public Mono<Vacancy> update(User actor, Long id, String rawName, String rawDescription, String rawRequirements,
            String rawResponsibilities, List<String> rawStages) {
        return update(actor, id, rawName, rawDescription, rawRequirements, rawResponsibilities, rawStages, null);
    }

    /** rawExpiresAt nulo mantém o prazo; texto vazio remove o prazo; uma data nova precisa estar no futuro. */
    public Mono<Vacancy> update(User actor, Long id, String rawName, String rawDescription, String rawRequirements,
            String rawResponsibilities, List<String> rawStages, String rawExpiresAt) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            String name = rawName == null ? null : Rules.vacancyName(rawName);
            String description = rawDescription == null ? null : Rules.vacancyDescription(rawDescription);
            List<String> newStages = rawStages == null ? null : Stages.normalize(rawStages);
            return get(id).flatMap(current -> {
                Instant expiresAt = rawExpiresAt == null ? current.expiresAt()
                        : rawExpiresAt.isBlank() ? null : parseExpiration(rawExpiresAt);
                String newName = name == null ? current.name() : name;
                var updated = new Vacancy(current.id(), newName, description == null ? current.description() : description,
                        rawRequirements == null ? current.requirements()
                                : Rules.optionalText(rawRequirements, "Os requisitos podem ter"),
                        rawResponsibilities == null ? current.responsibilities()
                                : Rules.optionalText(rawResponsibilities, "As responsabilidades podem ter"),
                        current.createdBy(), current.createdAt(), current.hasPhoto(),
                        newStages == null ? current.stages() : newStages, expiresAt, current.applicants());
                Mono<Vacancy> saved = newName.equals(current.name()) ? vacancies.save(updated)
                        : vacancies.existsByName(newName).flatMap(exists -> exists
                                ? Mono.<Vacancy>error(nameTaken())
                                : vacancies.save(updated));
                return newStages == null ? saved : stagesInUse(id, newStages).then(saved);
            });
        });
    }

    /** Vazio: sem prazo. Senão precisa ser uma data e hora ISO-8601 no futuro. */
    private Instant parseExpiration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Instant value;
        try {
            value = Instant.parse(raw.strip());
        } catch (java.time.format.DateTimeParseException e) {
            throw new DomainException.Invalid("Data de expiração inválida.");
        }
        if (!value.isAfter(clock.instant())) {
            throw new DomainException.Invalid("A expiração da vaga precisa ser uma data futura.");
        }
        return value;
    }

    /** Recusa tirar uma etapa em que já há candidatos. */
    private Mono<Void> stagesInUse(Long vacancyId, List<String> newStages) {
        return resumes.findAll(null, vacancyId)
                .filter(r -> newStages.stream().noneMatch(s -> s.equalsIgnoreCase(r.stage())))
                .next()
                .flatMap(r -> Mono.<Void>error(new DomainException.Conflict(
                        "A etapa \"" + r.stage() + "\" já tem candidatos. Mova-os antes de removê-la.")));
    }

    /** Apaga também as candidaturas e a foto da vaga (cascata no banco). */
    public Mono<Void> delete(User actor, Long id) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            return get(id).then(Mono.defer(() -> vacancies.deleteById(id)));
        });
    }

    // ---- foto ----

    /** JPEG, PNG ou WebP, até 2 MB, conferido pelo conteúdo. Troca a foto anterior. */
    public Mono<Void> uploadPhoto(User actor, Long id, byte[] content) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            ProfilePhoto photo = ProfilePhoto.of(content);
            return get(id).then(Mono.defer(() -> photos.save(id, photo)));
        });
    }

    public Mono<Void> removePhoto(User actor, Long id) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            return get(id).then(Mono.defer(() -> photos.delete(id)));
        });
    }

    public Mono<ProfilePhoto> photo(Long id) {
        return photos.find(id).switchIfEmpty(Mono.error(new DomainException.NotFound("Essa vaga não tem foto.")));
    }

    private static DomainException.NotFound notFound() {
        return new DomainException.NotFound("Vaga não encontrada.");
    }

    private static DomainException.Conflict nameTaken() {
        return new DomainException.Conflict("Já existe uma vaga com esse nome.");
    }
}
