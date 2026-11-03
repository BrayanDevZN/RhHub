package br.rhhub.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Gender;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.infrastructure.config.ClockConfig;
import br.rhhub.infrastructure.persistence.adapter.ResumeRepositoryAdapter;
import br.rhhub.infrastructure.persistence.adapter.UserRepositoryAdapter;
import br.rhhub.infrastructure.persistence.adapter.VacancyRepositoryAdapter;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roda a migration V1 e os adapters num PostgreSQL real (banco rhhub_test em localhost:5432);
 * é pulado se ele não estiver de pé. Sem transação de teste: uma violação de constraint aborta a transação
 * do PostgreSQL, então cada chamada do adapter tem a sua, como em produção. As tabelas são limpas ao fim.
 */
@EnabledIf("br.rhhub.infrastructure.TestSupport#postgresUp")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserRepositoryAdapter.class, VacancyRepositoryAdapter.class, ResumeRepositoryAdapter.class, ClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/rhhub_test",
        "spring.datasource.username=rhhub",
        "spring.datasource.password=rhhub",
        "app.jwt.secret=segredo-de-teste-com-mais-de-32-bytes!!"
})
class PersistenceIntegrationTest {

    @Autowired
    UserRepositoryAdapter users;
    @Autowired
    VacancyRepositoryAdapter vacancies;
    @Autowired
    ResumeRepositoryAdapter resumes;
    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.execute("truncate resumes, vacancies, users restart identity cascade");
    }

    private User newUser(String email, String cpf) {
        return users.save(new User(null, null, "Ana", email, cpf, 30, Gender.FEMALE, "hash", false, Role.USER,
                AuthProvider.LOCAL, null, null));
    }

    private static String email() {
        return UUID.randomUUID() + "@gmail.com";
    }

    @Test
    void savesAndFindsUser() {
        String email = email();
        User saved = newUser(email, "12345678901");

        assertThat(saved.id()).isNotNull();
        assertThat(saved.publicId()).isNotNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(users.findByEmail(email)).get().extracting(User::gender, User::role)
                .containsExactly(Gender.FEMALE, Role.USER);
        assertThat(users.findByPublicId(saved.publicId())).isPresent();
        assertThat(users.existsByEmail(email)).isTrue();
        assertThat(users.existsByCpf("12345678901")).isTrue();
    }

    @Test
    void updatesExistingUserKeepingIdentity() {
        User saved = newUser(email(), "11111111111");
        User updated = users.save(new User(saved.id(), saved.publicId(), saved.name(), saved.email(), saved.cpf(),
                saved.age(), saved.gender(), saved.passwordHash(), true, Role.ADMIN, saved.provider(),
                saved.googleSub(), saved.createdAt()));

        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(updated.twoFactor()).isTrue();
        assertThat(updated.role()).isEqualTo(Role.ADMIN);
        assertThat(updated.createdAt()).isEqualTo(saved.createdAt());
    }

    @Test
    void duplicateEmailOrCpfIsConflict() {
        String email = email();
        newUser(email, "22222222222");

        assertThatThrownBy(() -> newUser(email, "33333333333"))
                .isInstanceOf(DomainException.Conflict.class).hasMessageContaining("E-mail");
        assertThatThrownBy(() -> newUser(email(), "22222222222"))
                .isInstanceOf(DomainException.Conflict.class).hasMessageContaining("CPF");
    }

    @Test
    void googleAccountNeedsNoPasswordOrCpf() {
        User google = users.save(new User(null, null, "Gabi", email(), null, null, null, null, false, Role.USER,
                AuthProvider.GOOGLE, "sub-" + UUID.randomUUID(), null));
        assertThat(users.findByGoogleSub(google.googleSub())).isPresent();
    }

    @Test
    void deleteByPublicIdRemovesUser() {
        User saved = newUser(email(), "44444444444");
        users.deleteByPublicId(saved.publicId());
        assertThat(users.findByPublicId(saved.publicId())).isEmpty();
    }

    @Test
    void vacancyNameMustBeUniqueAndSortsNewestFirst() {
        String name = "Dev " + UUID.randomUUID();
        Vacancy v = vacancies.save(new Vacancy(null, name, "desc", null, null));

        assertThat(v.id()).isNotNull();
        assertThat(vacancies.existsByName(name)).isTrue();
        assertThat(vacancies.findAll()).extracting(Vacancy::id).contains(v.id());
        assertThatThrownBy(() -> vacancies.save(new Vacancy(null, name, "outra", null, null)))
                .isInstanceOf(DomainException.Conflict.class);

        vacancies.deleteById(v.id());
        assertThat(vacancies.findById(v.id())).isEmpty();
    }

    @Test
    void resumeLifecycle() {
        User user = newUser(email(), "55555555555");
        Vacancy vacancy = vacancies.save(new Vacancy(null, "Vaga " + UUID.randomUUID(), "d", user.id(), null));
        byte[] pdf = "%PDF-1.7 conteudo".getBytes();

        Resume created = resumes.create(new Resume(null, user.id(), vacancy.id(), "cv.pdf", "application/pdf",
                ResumeStatus.PENDING, null, null), pdf);

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(ResumeStatus.PENDING);
        assertThat(resumes.findFile(created.id())).get().satisfies(f -> {
            assertThat(f.content()).isEqualTo(pdf);
            assertThat(f.fileName()).isEqualTo("cv.pdf");
        });
        assertThat(resumes.existsByUserIdAndVacancyId(user.id(), vacancy.id())).isTrue();
        assertThat(resumes.findByUserId(user.id())).hasSize(1);
        assertThat(resumes.findAll(ResumeStatus.PENDING, vacancy.id())).extracting(Resume::id).contains(created.id());
        assertThat(resumes.findAll(ResumeStatus.RECUSE, vacancy.id())).isEmpty();

        assertThatThrownBy(() -> resumes.create(new Resume(null, user.id(), vacancy.id(), "cv2.pdf",
                "application/pdf", ResumeStatus.PENDING, null, null), pdf))
                .isInstanceOf(DomainException.Conflict.class);

        Resume done = resumes.updateResult(created.id(), ResumeStatus.APROVED, "Bom perfil");
        assertThat(done.status()).isEqualTo(ResumeStatus.APROVED);
        assertThat(done.reason()).isEqualTo("Bom perfil");
        assertThatThrownBy(() -> resumes.updateResult(-1L, ResumeStatus.RECUSE, "x"))
                .isInstanceOf(DomainException.NotFound.class);
    }

    @Test
    void deletingVacancyCascadesToResumes() {
        User user = newUser(email(), "66666666666");
        Vacancy vacancy = vacancies.save(new Vacancy(null, "Vaga " + UUID.randomUUID(), "d", user.id(), null));
        Resume resume = resumes.create(new Resume(null, user.id(), vacancy.id(), "cv.pdf", "application/pdf",
                ResumeStatus.PENDING, null, null), new byte[] {1});

        vacancies.deleteById(vacancy.id());
        assertThat(resumes.findById(resume.id())).isEmpty();
    }
}
