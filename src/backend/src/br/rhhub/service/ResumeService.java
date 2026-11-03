package br.rhhub.service;

import br.rhhub.application.port.out.DocumentTextExtractor;
import br.rhhub.application.port.out.ResumeAnalysisPublisher;
import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeFile;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Candidaturas: o candidato envia e acompanha; o dono e os admins leem e excluem. */
public class ResumeService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResumeService.class);

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final ResumeRepository resumes;
    private final VacancyRepository vacancies;
    private final DocumentTextExtractor extractor;
    private final ResumeAnalysisPublisher analysis;

    public ResumeService(ResumeRepository resumes, VacancyRepository vacancies, DocumentTextExtractor extractor,
            ResumeAnalysisPublisher analysis) {
        this.resumes = resumes;
        this.vacancies = vacancies;
        this.extractor = extractor;
        this.analysis = analysis;
    }

    /**
     * Valida o arquivo pelo conteúdo (PDF ou DOCX, até 5 MB), grava a candidatura como pending e pede a análise em
     * segundo plano (sem esperar o Kafka).
     */
    public Mono<Resume> submit(User actor, Long vacancyId, String fileName, byte[] content) {
        return Mono.defer(() -> {
            if (vacancyId == null) {
                throw new DomainException.Invalid("Escolha a vaga.");
            }
            if (content == null || content.length == 0) {
                throw new DomainException.Invalid("Envie o currículo.");
            }
            if (content.length > MAX_BYTES) {
                throw new DomainException.Invalid("O currículo pode ter no máximo 5 MB.");
            }
            var type = extractor.detect(content)
                    .orElseThrow(() -> new DomainException.Invalid("O currículo precisa ser PDF ou DOCX."));
            String name = cleanName(fileName, type.name().toLowerCase());
            return vacancies.findById(vacancyId)
                    .switchIfEmpty(Mono.error(new DomainException.NotFound("Vaga não encontrada.")))
                    .then(Mono.defer(() -> resumes.existsByUserIdAndVacancyId(actor.id(), vacancyId)))
                    .flatMap(exists -> exists
                            ? Mono.<Resume>error(new DomainException.Conflict("Você já se candidatou a essa vaga."))
                            : resumes.create(new Resume(null, actor.id(), vacancyId, name, type.contentType(),
                                    ResumeStatus.PENDING, null, null), content))
                    .map(saved -> {
                        enqueueInBackground(saved.id());
                        return saved;
                    });
        });
    }

    /**
     * O envio responde assim que o currículo está gravado. Avisar a fila de análise acontece em segundo plano: a
     * resposta nunca espera o Kafka (rede, proxy, conexão parada). Se o aviso falhar, a candidatura continua pending e
     * o consumer a reenfileira depois (PendingResumeRepublisher), então nada se perde.
     */
    private void enqueueInBackground(Long resumeId) {
        analysis.publish(resumeId).subscribe(
                done -> { },
                error -> log.warn("Candidatura {}: não foi possível enfileirar a análise agora; será reenfileirada: {}",
                        resumeId, error.toString()));
    }

    public Flux<Resume> listMine(User actor) {
        return resumes.findByUserId(actor.id());
    }

    /** Só admin. Filtros opcionais. */
    public Flux<Resume> listAll(User actor, ResumeStatus status, Long vacancyId) {
        return Flux.defer(() -> {
            requireAdmin(actor);
            return resumes.findAll(status, vacancyId);
        });
    }

    public Mono<Resume> get(User actor, Long id) {
        return find(actor, id);
    }

    public Mono<ResumeFile> file(User actor, Long id) {
        return find(actor, id).then(Mono.defer(() -> resumes.findFile(id)))
                .switchIfEmpty(Mono.error(new DomainException.NotFound("Candidatura não encontrada.")));
    }

    public Mono<Void> delete(User actor, Long id) {
        return find(actor, id).then(Mono.defer(() -> resumes.deleteById(id)));
    }

    private Mono<Resume> find(User actor, Long id) {
        return resumes.findById(id)
                .switchIfEmpty(Mono.error(new DomainException.NotFound("Candidatura não encontrada.")))
                .flatMap(resume -> resume.userId().equals(actor.id()) || actor.isAdmin()
                        ? Mono.just(resume)
                        : Mono.<Resume>error(new DomainException.Forbidden("Essa candidatura não é sua.")));
    }

    private static void requireAdmin(User actor) {
        if (!actor.isAdmin()) {
            throw new DomainException.Forbidden("Apenas administradores podem fazer isso.");
        }
    }

    /** Só o nome do arquivo (sem pasta), sem caracteres de controle e com no máximo 255 caracteres. */
    static String cleanName(String raw, String extension) {
        String name = raw == null ? "" : raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "curriculo." + extension;
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
