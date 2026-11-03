package br.rhhub.service;

import br.rhhub.application.port.in.AnalyzeResume;
import br.rhhub.application.port.out.AiProvider;
import br.rhhub.application.port.out.DocumentTextExtractor;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AnalysisResult;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Análise de uma candidatura: lê o currículo, pergunta à IA, grava o resultado e pede o e-mail.
 *
 * <ul>
 *   <li>Candidatura que não existe mais ou que já não está {@code pending} é ignorada (idempotência).</li>
 *   <li>Arquivo ilegível não derruba o job: a IA recebe um aviso e recusa, como o prompt manda.</li>
 *   <li>Resposta da IA fora do formato falha com {@code Unprocessable}: a candidatura continua pendente e o job
 *       é repetido (e, no fim, vai para a DLQ).</li>
 *   <li>O resultado é gravado antes de pedir o e-mail. Se o pedido do e-mail falhar, o resultado volta a
 *       {@code pending}, para a repetição do job analisar de novo em vez de dar a candidatura por concluída
 *       sem e-mail.</li>
 * </ul>
 */
public class AnalyzeResumeService implements AnalyzeResume {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeResumeService.class);

    /** Limite do texto enviado à IA, para não estourar o contexto nem o limite de tokens do provedor. */
    static final int MAX_RESUME_CHARS = 20_000;

    private final ResumeRepository resumes;
    private final VacancyRepository vacancies;
    private final UserRepository users;
    private final DocumentTextExtractor extractor;
    private final AiProvider ai;
    private final EmailPublisher emails;
    private final String prompt;

    public AnalyzeResumeService(ResumeRepository resumes, VacancyRepository vacancies, UserRepository users,
            DocumentTextExtractor extractor, AiProvider ai, EmailPublisher emails, String prompt) {
        this.resumes = resumes;
        this.vacancies = vacancies;
        this.users = users;
        this.extractor = extractor;
        this.ai = ai;
        this.emails = emails;
        this.prompt = prompt;
    }

    @Override
    public Mono<Void> execute(Long resumeId) {
        return resumes.findById(resumeId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Candidatura {} não existe mais: nada a analisar", resumeId);
                    return Mono.empty();
                }))
                .filter(resume -> {
                    if (resume.status() != ResumeStatus.PENDING) {
                        log.info("Candidatura {} já está {}: análise ignorada", resumeId, resume.status().value());
                        return false;
                    }
                    return true;
                })
                .flatMap(this::analyze)
                .then();
    }

    private Mono<Void> analyze(Resume resume) {
        return Mono.zip(vacancies.findById(resume.vacancyId()), users.findById(resume.userId()),
                        resumes.findFile(resume.id()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Candidatura {} perdeu a vaga, o usuário ou o arquivo: análise ignorada", resume.id());
                    return Mono.empty();
                }))
                .flatMap(data -> {
                    Vacancy vacancy = data.getT1();
                    User user = data.getT2();
                    return Mono.fromCallable(() -> resumeText(data.getT3().content(), resume.id()))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(text -> ai.complete(prompt, input(vacancy, user, text)))
                            .map(AnalysisResult::parse)
                            .flatMap(result -> save(resume, user, result));
                });
    }

    private Mono<Void> save(Resume resume, User user, AnalysisResult result) {
        log.info("Candidatura {} analisada: {}", resume.id(), result.decision().value());
        return resumes.updateResult(resume.id(), result.decision(), result.reason())
                .then(emails.publish(resume.id(), EmailMessage.html(user.email(), result.subject(), result.html()))
                        .onErrorResume(error -> {
                            log.error("Candidatura {}: e-mail não enfileirado, voltando para pending", resume.id(), error);
                            return resumes.updateResult(resume.id(), ResumeStatus.PENDING, null)
                                    .onErrorResume(undo -> {
                                        log.error("Candidatura {}: não foi possível voltar para pending", resume.id(), undo);
                                        return Mono.empty();
                                    })
                                    .then(Mono.error(error));
                        }));
    }

    /** Texto do currículo, ou um aviso quando não dá para ler: a IA recebe o aviso e recusa, como o prompt manda. */
    private String resumeText(byte[] file, Long resumeId) {
        var type = extractor.detect(file);
        if (type.isEmpty()) {
            return "[O arquivo enviado não pôde ser identificado como PDF ou DOCX.]";
        }
        String text;
        try {
            text = extractor.extract(file, type.get());
        } catch (DomainException.Unprocessable e) {
            log.warn("Candidatura {}: arquivo ilegível ({})", resumeId, e.getMessage());
            return "[O arquivo enviado está corrompido e não pôde ser lido.]";
        }
        if (text == null || text.isBlank()) {
            return "[O documento não contém texto legível.]";
        }
        return text.length() <= MAX_RESUME_CHARS
                ? text
                : text.substring(0, MAX_RESUME_CHARS) + "\n[texto truncado por tamanho]";
    }

    static String input(Vacancy vacancy, User user, String resumeText) {
        var job = new StringBuilder("JOB_DESCRIPTION:\nTitle: " + vacancy.name() + "\n" + vacancy.description());
        if (vacancy.requirements() != null) {
            job.append("\n\nREQUIREMENTS:\n").append(vacancy.requirements());
        }
        if (vacancy.responsibilities() != null) {
            job.append("\n\nRESPONSIBILITIES:\n").append(vacancy.responsibilities());
        }
        return job
                + "\n\nCANDIDATE_NAME: " + user.name()
                + "\n\nCANDIDATE_RESUME:\n" + resumeText;
    }
}
