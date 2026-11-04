package br.rhhub.service;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.infra.document.PdfDocxTextExtractor;
import br.rhhub.infra.email.HtmlEmailComposer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

@UnitTest
class NotificationServiceTest {

    private static final byte[] PDF = "%PDF-1.7 conteudo".getBytes(StandardCharsets.UTF_8);

    private Fakes.Users users;
    private Fakes.Vacancies vacancies;
    private Fakes.Resumes resumes;
    private Fakes.Notifications repo;
    private NotificationService notifications;
    private VacancyService vacancyService;
    private ResumeService resumeService;
    private ResumeStageService stageService;
    private User admin;
    private User otherAdmin;
    private User ana;
    private User bia;

    private User user(String email, Role role) {
        return users.save(new User(null, UUID.randomUUID(), email.split("@")[0], email, null, 30, null, "hash", false, role,
                AuthProvider.LOCAL, null, null)).block();
    }

    @BeforeEach
    void setUp() {
        users = new Fakes.Users();
        vacancies = new Fakes.Vacancies();
        resumes = new Fakes.Resumes();
        repo = new Fakes.Notifications();
        notifications = new NotificationService(repo, users);
        var policy = new AccessPolicy("root@gmail.com");
        vacancyService = new VacancyService(vacancies, vacancies.photos, policy, resumes);
        resumeService = new ResumeService(resumes, vacancies, new PdfDocxTextExtractor(), new Fakes.Analysis(),
                Clock.systemUTC(), notifications);
        stageService = new ResumeStageService(resumes, vacancies, users, (id, m) -> Mono.empty(), new HtmlEmailComposer(),
                policy, notifications);
        admin = user("admin@gmail.com", Role.ADMIN);
        otherAdmin = user("chefe@gmail.com", Role.ADMIN);
        ana = user("ana@gmail.com", Role.USER);
        bia = user("bia@gmail.com", Role.USER);
    }

    @Test
    void aNewApplicationNotifiesEveryAdminAndNobodyElse() {
        var vacancy = vacancyService.create(admin, "Dev Java", "d", null, null).block();
        resumeService.submit(ana, vacancy.id(), "cv.pdf", PDF).block();

        for (User a : new User[] {admin, otherAdmin}) {
            var inbox = notifications.inbox(a).block();
            assertThat(inbox.unread()).isEqualTo(1);
            assertThat(inbox.items().get(0).message()).contains("ana").contains("Dev Java");
            assertThat(inbox.items().get(0).link()).isEqualTo("/admin/candidaturas");
        }
        assertThat(notifications.inbox(ana).block().items()).isEmpty();
        assertThat(notifications.inbox(bia).block().items()).isEmpty();
    }

    @Test
    void movingACandidateNotifiesHimWithTheStage() {
        var vacancy = vacancyService.create(admin, "Dev Java", "d", null, null).block();
        Resume resume = resumes.create(new Resume(null, ana.id(), vacancy.id(), "cv.pdf", "application/pdf",
                ResumeStatus.APROVED, null, null), new byte[] {1}).block();

        stageService.move(admin, resume.id(), "Entrevista").block();

        var items = notifications.inbox(ana).block().items();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).message()).contains("Entrevista").contains("Dev Java").contains("2 de 3");
        assertThat(items.get(0).link()).isEqualTo("/candidaturas");
        assertThat(notifications.inbox(bia).block().items()).isEmpty();
    }

    @Test
    void readingIsPerUserAndOthersCannotTouchMyNotifications() {
        notifications.user(ana.id(), "A", "um", null).block();
        notifications.user(ana.id(), "B", "dois", "/vagas").block();
        notifications.user(bia.id(), "C", "tres", null).block();

        var inbox = notifications.inbox(ana).block();
        assertThat(inbox.unread()).isEqualTo(2);
        assertThat(inbox.items()).extracting("title").containsExactly("B", "A"); // mais recente primeiro

        Long mine = inbox.items().get(0).id();
        assertThat(notifications.markRead(bia, mine).block()).isFalse(); // de outro usuário
        assertThat(notifications.inbox(ana).block().unread()).isEqualTo(2);
        assertThat(notifications.markRead(ana, mine).block()).isTrue();
        assertThat(notifications.inbox(ana).block().unread()).isEqualTo(1);
        assertThat(notifications.markRead(ana, 999L).block()).isFalse();

        notifications.markAllRead(ana).block();
        assertThat(notifications.inbox(ana).block().unread()).isZero();
        assertThat(notifications.inbox(bia).block().unread()).isEqualTo(1); // a de Bia não foi tocada
    }

    @Test
    void aFailureWhileSavingNeverBreaksTheCaller() {
        var broken = new NotificationService(new Fakes.Notifications() {
            @Override public Mono<br.rhhub.domain.model.Notification> create(br.rhhub.domain.model.Notification n) {
                return Mono.error(new IllegalStateException("banco fora"));
            }
        }, users);
        broken.user(ana.id(), "x", "y", null).block();
        broken.admins("x", "y", null).block();
    }
}
