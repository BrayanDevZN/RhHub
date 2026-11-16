package br.rhhub.service;

import br.rhhub.application.port.out.EmailComposer;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.application.port.out.Notifier;
import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Etapas do processo seletivo depois da Triagem. Só admin move o candidato, e só quem foi aprovado na triagem avança.
 * A cada mudança o candidato recebe um e-mail dizendo em que etapa está.
 */
public class ResumeStageService {

    private static final Logger log = LoggerFactory.getLogger(ResumeStageService.class);

    private final ResumeRepository resumes;
    private final VacancyRepository vacancies;
    private final UserRepository users;
    private final EmailPublisher emails;
    private final EmailComposer composer;
    private final AccessPolicy policy;

    private final Notifier notifier;

    public ResumeStageService(ResumeRepository resumes, VacancyRepository vacancies, UserRepository users,
            EmailPublisher emails, EmailComposer composer, AccessPolicy policy) {
        this(resumes, vacancies, users, emails, composer, policy, Notifier.NONE);
    }

    public ResumeStageService(ResumeRepository resumes, VacancyRepository vacancies, UserRepository users,
            EmailPublisher emails, EmailComposer composer, AccessPolicy policy, Notifier notifier) {
        this.notifier = notifier;
        this.resumes = resumes;
        this.vacancies = vacancies;
        this.users = users;
        this.emails = emails;
        this.composer = composer;
        this.policy = policy;
    }

    public Mono<Resume> move(User actor, Long resumeId, String rawStage) {
        return Mono.defer(() -> {
            policy.requireAdmin(actor);
            if (rawStage == null || rawStage.isBlank()) {
                throw new DomainException.Invalid("Informe a etapa.");
            }
            return resumes.findById(resumeId)
                    .switchIfEmpty(Mono.error(new DomainException.NotFound("Candidatura não encontrada.")))
                    .flatMap(resume -> {
                        if (resume.status() != ResumeStatus.APROVED) {
                            return Mono.<Resume>error(new DomainException.Unprocessable(
                                    "Só candidaturas aprovadas na triagem podem mudar de etapa."));
                        }
                        return vacancies.findById(resume.vacancyId())
                                .switchIfEmpty(Mono.error(new DomainException.NotFound("Vaga não encontrada.")))
                                .flatMap(vacancy -> {
                                    var stages = vacancy.stages();
                                    int index = indexOf(stages, rawStage);
                                    if (index < 0) {
                                        return Mono.<Resume>error(new DomainException.Invalid(
                                                "Essa etapa não existe nessa vaga."));
                                    }
                                    String stage = stages.get(index);
                                    if (stage.equals(resume.stage())) {
                                        return Mono.just(resume);
                                    }
                                    return resumes.updateStage(resume.id(), stage)
                                            .flatMap(updated -> notify(updated, vacancy.name(), stage, index + 1,
                                                    stages.size())
                                                    .then(notifier.user(updated.userId(), "Sua candidatura avançou",
                                                            vacancy.name() + ": agora você está na etapa " + stage
                                                                    + " (" + (index + 1) + " de " + stages.size() + ").",
                                                            "/candidaturas"))
                                                    .thenReturn(updated));
                                });
                    });
        });
    }

    /** O aviso é um extra: se o e-mail não puder ser enfileirado, a etapa continua mudada e o erro vai para o log. */
    private Mono<Void> notify(Resume resume, String vacancyName, String stage, int position, int total) {
        return users.findById(resume.userId())
                .flatMap(user -> emails.publish(resume.id(),
                        composer.stageChanged(user.email(), user.name(), vacancyName, stage, position, total)))
                .onErrorResume(error -> {
                    log.warn("Candidatura {}: e-mail da etapa {} não enfileirado: {}", resume.id(), stage, error.toString());
                    return Mono.empty();
                });
    }

    private static int indexOf(java.util.List<String> stages, String wanted) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).equalsIgnoreCase(wanted.strip())) {
                return i;
            }
        }
        return -1;
    }
}
