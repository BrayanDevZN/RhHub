package br.rhhub.service;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.infra.document.PdfDocxTextExtractor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UnitTest
class VacancyExpirationTest {

    private static final byte[] PDF = "%PDF-1.7 conteudo".getBytes(StandardCharsets.UTF_8);

    private Fakes.Vacancies vacancies;
    private VacancyService service;
    private ResumeService resumeService;
    private User admin;
    private User ana;

    private User user(String email, Role role) {
        return new Fakes.Users().save(new User(null, UUID.randomUUID(), email.split("@")[0], email, null, 30, null, "hash",
                false, role, AuthProvider.LOCAL, null, null)).block();
    }

    @BeforeEach
    void setUp() {
        vacancies = new Fakes.Vacancies();
        var resumes = new Fakes.Resumes();
        service = new VacancyService(vacancies, vacancies.photos, new AccessPolicy("root@gmail.com"), resumes);
        resumeService = new ResumeService(resumes, vacancies, new PdfDocxTextExtractor(), new Fakes.Analysis());
        admin = user("admin@gmail.com", Role.ADMIN);
        ana = user("ana@gmail.com", Role.USER);
    }

    private String in(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).toString();
    }

    @Test
    void expirationIsOptionalAndMustBeInTheFuture() {
        assertThat(service.create(admin, "Sem prazo", "d", null, null).block().expiresAt()).isNull();
        var withDate = service.create(admin, "Com prazo", "d", null, null, null, in(10)).block();
        assertThat(withDate.expiresAt()).isAfter(Instant.now());
        assertThat(withDate.isExpired(Instant.now())).isFalse();
        assertThatThrownBy(() -> service.create(admin, "Passado", "d", null, null, null, in(-1)).block())
                .isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> service.create(admin, "Lixo", "d", null, null, null, "amanhã").block())
                .isInstanceOf(DomainException.Invalid.class);
    }

    @Test
    void updateKeepsChangesOrClearsTheDeadline() {
        var vacancy = service.create(admin, "Dev", "d", null, null, null, in(10)).block();
        var kept = service.update(admin, vacancy.id(), null, "nova", null, null, null, null).block();
        assertThat(kept.expiresAt()).isEqualTo(vacancy.expiresAt());
        var changed = service.update(admin, vacancy.id(), null, null, null, null, null, in(20)).block();
        assertThat(changed.expiresAt()).isAfter(vacancy.expiresAt());
        var cleared = service.update(admin, vacancy.id(), null, null, null, null, null, "").block();
        assertThat(cleared.expiresAt()).isNull();
    }

    @Test
    void nobodyAppliesToAnExpiredVacancyButOpenOnesStillWork() {
        var open = service.create(admin, "Aberta", "d", null, null, null, in(5)).block();
        // uma vaga que já venceu (gravada direto: o serviço só aceita datas futuras)
        var expired = vacancies.save(new Vacancy(null, "Vencida", "d", null, null, admin.id(), null, false, null,
                Instant.now().minusSeconds(60), 0)).block();
        assertThat(expired.isExpired(Instant.now())).isTrue();

        assertThatThrownBy(() -> resumeService.submit(ana, expired.id(), "cv.pdf", PDF).block())
                .isInstanceOf(DomainException.Unprocessable.class).hasMessageContaining("encerrada");
        assertThat(resumeService.submit(ana, open.id(), "cv.pdf", PDF).block().vacancyId()).isEqualTo(open.id());
    }
}
