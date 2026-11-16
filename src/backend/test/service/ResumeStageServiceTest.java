package br.rhhub.service;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.domain.rule.Stages;
import br.rhhub.infra.email.HtmlEmailComposer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

@UnitTest
class ResumeStageServiceTest {

    private Fakes.Users users;
    private Fakes.Vacancies vacancies;
    private Fakes.Resumes resumes;
    private final List<EmailMessage> sent = new ArrayList<>();
    private boolean emailFails;
    private VacancyService vacancyService;
    private ResumeStageService service;
    private User admin;
    private User ana;
    private Long vacancyId;

    private User user(String email, Role role) {
        return users.save(new User(null, UUID.randomUUID(), email.split("@")[0], email, null, 30, null, "hash", false, role,
                AuthProvider.LOCAL, null, null)).block();
    }

    @BeforeEach
    void setUp() {
        users = new Fakes.Users();
        vacancies = new Fakes.Vacancies();
        resumes = new Fakes.Resumes();
        var policy = new AccessPolicy("root@gmail.com");
        EmailPublisher emails = (id, message) -> {
            if (emailFails) {
                return Mono.error(new IllegalStateException("kafka fora do ar"));
            }
            sent.add(message);
            return Mono.empty();
        };
        vacancyService = new VacancyService(vacancies, vacancies.photos, policy, resumes);
        service = new ResumeStageService(resumes, vacancies, users, emails, new HtmlEmailComposer(), policy);
        admin = user("admin@gmail.com", Role.ADMIN);
        ana = user("ana@gmail.com", Role.USER);
        vacancyId = vacancyService.create(admin, "Dev Java", "Backend", null, null).block().id();
    }

    private Resume apply(ResumeStatus status) {
        return resumes.create(new Resume(null, ana.id(), vacancyId, "cv.pdf", "application/pdf", status, null, null),
                new byte[] {1}).block();
    }

    @Test
    void aVacancyWithoutStagesGetsTheDefaultOnes() {
        assertThat(vacancies.byId.get(vacancyId).stages()).containsExactly("Triagem", "Entrevista", "Contratado");
    }

    @Test
    void screeningIsMandatoryAndAlwaysFirst() {
        var vacancy = vacancyService.create(admin, "Dev Go", "Backend", null, null, List.of("Teste técnico", "Contratado"))
                .block();
        assertThat(vacancy.stages()).containsExactly("Triagem", "Teste técnico", "Contratado");
        var onlyScreening = vacancyService.create(admin, "Dev C", "Backend", null, null, List.of("Triagem")).block();
        assertThat(onlyScreening.stages()).containsExactly("Triagem");
    }

    @Test
    void anApprovedCandidateMovesAndGetsAnEmail() {
        Resume resume = apply(ResumeStatus.APROVED);
        assertThat(resume.stage()).isEqualTo(Stages.SCREENING);

        Resume moved = service.move(admin, resume.id(), "entrevista").block();

        assertThat(moved.stage()).isEqualTo("Entrevista");
        assertThat(resumes.byId.get(resume.id()).stage()).isEqualTo("Entrevista");
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).to()).isEqualTo("ana@gmail.com");
        assertThat(sent.get(0).content()).contains("Entrevista").contains("Dev Java").contains("etapa 2 de 3");
    }

    @Test
    void movingToTheSameStageDoesNotSendAnotherEmail() {
        Resume resume = apply(ResumeStatus.APROVED);
        service.move(admin, resume.id(), "Triagem").block();
        assertThat(sent).isEmpty();
    }

    @Test
    void onlyApprovedCandidatesAdvance() {
        assertThatThrownBy(() -> service.move(admin, apply(ResumeStatus.PENDING).id(), "Entrevista").block())
                .isInstanceOf(DomainException.Unprocessable.class);
    }

    @Test
    void onlyAdminsMoveAndTheStageMustExist() {
        Resume resume = apply(ResumeStatus.APROVED);
        assertThatThrownBy(() -> service.move(ana, resume.id(), "Entrevista").block())
                .isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> service.move(admin, resume.id(), "Churrasco").block())
                .isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> service.move(admin, 999L, "Entrevista").block())
                .isInstanceOf(DomainException.NotFound.class);
    }

    @Test
    void aFailingEmailDoesNotUndoTheMove() {
        emailFails = true;
        Resume resume = apply(ResumeStatus.APROVED);
        assertThat(service.move(admin, resume.id(), "Contratado").block().stage()).isEqualTo("Contratado");
    }

    @Test
    void aStageWithCandidatesCannotBeRemoved() {
        Resume resume = apply(ResumeStatus.APROVED);
        service.move(admin, resume.id(), "Entrevista").block();
        assertThatThrownBy(() -> vacancyService.update(admin, vacancyId, null, null, null, null,
                List.of("Triagem", "Contratado")).block()).isInstanceOf(DomainException.Conflict.class);
        var ok = vacancyService.update(admin, vacancyId, null, null, null, null,
                List.of("Triagem", "Entrevista", "Teste")).block();
        assertThat(ok.stages()).containsExactly("Triagem", "Entrevista", "Teste");
    }

    @Test
    void normalizeCleansUpTheList() {
        assertThat(Stages.normalize(List.of(" Entrevista ", "entrevista", "", "Final   round")))
                .containsExactly("Triagem", "Entrevista", "Final round");
        assertThat(Stages.normalize(null)).isEqualTo(Stages.DEFAULT);
        assertThatThrownBy(() -> Stages.normalize(List.of("x".repeat(41)))).isInstanceOf(DomainException.Invalid.class);
        assertThat(Stages.parse(Stages.join(Stages.DEFAULT))).isEqualTo(Stages.DEFAULT);
    }
}
