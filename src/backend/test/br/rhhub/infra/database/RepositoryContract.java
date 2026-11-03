package br.rhhub.infra.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.DeadLetter;
import br.rhhub.domain.model.DeadLetterStatus;
import br.rhhub.domain.model.Gender;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.infra.database.adapter.DeadLetterRepositoryAdapter;
import br.rhhub.domain.model.ProfilePhoto;
import br.rhhub.infra.database.adapter.ProfilePhotoRepositoryAdapter;
import br.rhhub.infra.database.adapter.VacancyPhotoRepositoryAdapter;
import br.rhhub.infra.database.adapter.ResumeRepositoryAdapter;
import br.rhhub.infra.database.adapter.UserRepositoryAdapter;
import br.rhhub.infra.database.adapter.VacancyRepositoryAdapter;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Os mesmos testes rodam no PostgreSQL (R2DBC) e no SQLite: o comportamento dos adapters tem de ser igual. */
abstract class RepositoryContract {

    abstract SqlExecutor executor();

    private UserRepositoryAdapter users;
    private VacancyRepositoryAdapter vacancies;
    private ResumeRepositoryAdapter resumes;

    private void init() {
        if (users == null) {
            users = new UserRepositoryAdapter(executor(), Clock.systemUTC());
            vacancies = new VacancyRepositoryAdapter(executor(), Clock.systemUTC());
            resumes = new ResumeRepositoryAdapter(executor(), Clock.systemUTC());
        }
    }

    @AfterEach
    void clean() {
        for (String table : new String[] {"dead_letters", "resumes", "vacancy_photos", "vacancies", "user_photos", "users"}) {
            executor().update("DELETE FROM " + table, Params.of()).block();
        }
    }

    private User newUser(String email, String cpf) {
        init();
        return users.save(new User(null, null, "Ana", email, cpf, 30, Gender.FEMALE, "hash", true, Role.USER,
                AuthProvider.LOCAL, null, null)).block();
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
        assertThat(saved.twoFactor()).isTrue();
        User found = users.findByEmail(email).block();
        assertThat(found).isEqualTo(saved);
        assertThat(found.gender()).isEqualTo(Gender.FEMALE);
        assertThat(users.findByPublicId(saved.publicId()).block()).isEqualTo(saved);
        assertThat(users.findById(saved.id()).block()).isEqualTo(saved);
        assertThat(users.findById(-1L).block()).isNull();
        assertThat(users.findByEmail("nao@existe.com").block()).isNull();
        assertThat(users.existsByEmail(email).block()).isTrue();
        assertThat(users.existsByEmail("nao@existe.com").block()).isFalse();
        assertThat(users.existsByCpf("12345678901").block()).isTrue();
        assertThat(users.findAll().collectList().block()).extracting(User::id).contains(saved.id());
    }

    @Test
    void updatesExistingUserKeepingIdentity() {
        User saved = newUser(email(), "11111111111");
        User updated = users.save(new User(saved.id(), saved.publicId(), "Ana Maria", saved.email(), saved.cpf(),
                saved.age(), saved.gender(), saved.passwordHash(), false, Role.ADMIN, saved.provider(),
                saved.googleSub(), saved.createdAt())).block();

        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(updated.publicId()).isEqualTo(saved.publicId());
        assertThat(updated.name()).isEqualTo("Ana Maria");
        assertThat(updated.twoFactor()).isFalse();
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
        init();
        User google = users.save(new User(null, null, "Gabi", email(), null, null, null, null, false, Role.USER,
                AuthProvider.GOOGLE, "sub-" + UUID.randomUUID(), null)).block();

        assertThat(google.cpf()).isNull();
        assertThat(google.age()).isNull();
        assertThat(google.gender()).isNull();
        assertThat(users.findByGoogleSub(google.googleSub()).block()).isEqualTo(google);
    }

    @Test
    void deleteByPublicIdRemovesUser() {
        User saved = newUser(email(), "44444444444");
        users.deleteByPublicId(saved.publicId()).block();
        assertThat(users.findByPublicId(saved.publicId()).block()).isNull();
    }

    @Test
    void vacancyNameIsUniqueAndListsNewestFirst() {
        init();
        String name = "Dev " + UUID.randomUUID();
        Vacancy first = vacancies.save(new Vacancy(null, name, "desc", null, null)).block();
        Vacancy second = vacancies.save(new Vacancy(null, "Outra " + UUID.randomUUID(), "d", null, null)).block();

        assertThat(first.id()).isNotNull();
        assertThat(first.createdBy()).isNull();
        assertThat(vacancies.existsByName(name).block()).isTrue();
        assertThat(vacancies.findAll().collectList().block()).extracting(Vacancy::id)
                .containsExactly(second.id(), first.id());
        assertThatThrownBy(() -> vacancies.save(new Vacancy(null, name, "outra", null, null)).block())
                .isInstanceOf(DomainException.Conflict.class);

        Vacancy renamed = vacancies.save(new Vacancy(first.id(), "Novo " + name, "nova", null, first.createdAt())).block();
        assertThat(renamed.description()).isEqualTo("nova");
        vacancies.deleteById(first.id()).block();
        assertThat(vacancies.findById(first.id()).block()).isNull();
    }

    @Test
    void resumeLifecycle() {
        User user = newUser(email(), "55555555555");
        Vacancy vacancy = vacancies.save(new Vacancy(null, "Vaga " + UUID.randomUUID(), "d", user.id(), null)).block();
        byte[] pdf = new byte[] {'%', 'P', 'D', 'F', 0, (byte) 0xFF, 7};

        Resume created = resumes.create(new Resume(null, user.id(), vacancy.id(), "cv.pdf", "application/pdf",
                ResumeStatus.PENDING, null, null), pdf).block();

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(ResumeStatus.PENDING);
        assertThat(created.reason()).isNull();
        var file = resumes.findFile(created.id()).block();
        assertThat(file.content()).isEqualTo(pdf);
        assertThat(file.fileName()).isEqualTo("cv.pdf");
        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThat(resumes.existsByUserIdAndVacancyId(user.id(), vacancy.id()).block()).isTrue();
        assertThat(resumes.findByUserId(user.id()).collectList().block()).hasSize(1);
        assertThat(resumes.findAll(ResumeStatus.PENDING, vacancy.id()).collectList().block())
                .extracting(Resume::id).containsExactly(created.id());
        assertThat(resumes.findAll(ResumeStatus.RECUSE, null).collectList().block()).isEmpty();
        assertThat(resumes.findAll(null, null).collectList().block()).hasSize(1);

        assertThatThrownBy(() -> resumes.create(new Resume(null, user.id(), vacancy.id(), "cv2.pdf", "application/pdf",
                ResumeStatus.PENDING, null, null), pdf).block()).isInstanceOf(DomainException.Conflict.class);

        Resume done = resumes.updateResult(created.id(), ResumeStatus.APROVED, "Bom perfil").block();
        assertThat(done.status()).isEqualTo(ResumeStatus.APROVED);
        assertThat(done.reason()).isEqualTo("Bom perfil");
        assertThatThrownBy(() -> resumes.updateResult(-1L, ResumeStatus.RECUSE, "x").block())
                .isInstanceOf(DomainException.NotFound.class);
    }

    @Test
    void resumeForMissingUserOrVacancyIsNotFound() {
        init();
        assertThatThrownBy(() -> resumes.create(new Resume(null, -1L, -1L, "cv.pdf", "application/pdf",
                ResumeStatus.PENDING, null, null), new byte[] {1}).block()).isInstanceOf(DomainException.NotFound.class);
    }

    @Test
    void deletingVacancyOrUserCascadesToResumes() {
        User user = newUser(email(), "66666666666");
        Vacancy v1 = vacancies.save(new Vacancy(null, "A " + UUID.randomUUID(), "d", user.id(), null)).block();
        Vacancy v2 = vacancies.save(new Vacancy(null, "B " + UUID.randomUUID(), "d", user.id(), null)).block();
        Resume r1 = resumes.create(new Resume(null, user.id(), v1.id(), "a.pdf", "application/pdf",
                ResumeStatus.PENDING, null, null), new byte[] {1}).block();
        Resume r2 = resumes.create(new Resume(null, user.id(), v2.id(), "b.pdf", "application/pdf",
                ResumeStatus.PENDING, null, null), new byte[] {2}).block();

        vacancies.deleteById(v1.id()).block();
        assertThat(resumes.findById(r1.id()).block()).isNull();

        users.deleteByPublicId(user.publicId()).block();
        assertThat(resumes.findById(r2.id()).block()).isNull();
        // vaga sobrevive ao admin que a criou, com created_by nulo
        assertThat(vacancies.findById(v2.id()).block().createdBy()).isNull();
    }

    private DeadLetterRepositoryAdapter deadLetters() {
        return new DeadLetterRepositoryAdapter(executor(), Clock.systemUTC());
    }

    @Test
    void deadLetterKeepsTheFailedJobAndItsOrigin() {
        var repo = deadLetters();
        var error = new IllegalStateException("LLM fora do ar");
        var saved = repo.save(DeadLetter.failed("resume.analysis", "42", 1, 17L, "{\"resumeId\":42}", error, 3)).block();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.status()).isEqualTo(DeadLetterStatus.FAILED);
        assertThat(saved.topic()).isEqualTo("resume.analysis");
        assertThat(saved.key()).isEqualTo("42");
        assertThat(saved.partition()).isEqualTo(1);
        assertThat(saved.offset()).isEqualTo(17L);
        assertThat(saved.payload()).isEqualTo("{\"resumeId\":42}");
        assertThat(saved.errorType()).isEqualTo("java.lang.IllegalStateException");
        assertThat(saved.errorMessage()).isEqualTo("LLM fora do ar");
        assertThat(saved.attempts()).isEqualTo(3);
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.resolvedAt()).isNull();
        assertThat(repo.findById(saved.id()).block()).isEqualTo(saved);
    }

    @Test
    void sameKafkaOriginIsNeverSavedTwice() {
        var repo = deadLetters();
        var first = repo.save(DeadLetter.failed("email.send", "7", 0, 5L, "{}", new RuntimeException("a"), 3)).block();
        var again = repo.save(DeadLetter.failed("email.send", "7", 0, 5L, "{}", new RuntimeException("b"), 3)).block();

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.errorMessage()).isEqualTo("a"); // devolve o que já estava gravado
        assertThat(repo.findAll(null).collectList().block()).hasSize(1);

        var otherOffset = repo.save(DeadLetter.failed("email.send", "7", 0, 6L, "{}", new RuntimeException("c"), 3)).block();
        assertThat(otherOffset.id()).isNotEqualTo(first.id());
    }

    @Test
    void failureWithoutKafkaOriginAndWithoutMessageIsAccepted() {
        var repo = deadLetters();
        var saved = repo.save(DeadLetter.failed("resume.analysis", null, null, null, "x", new RuntimeException(), 1)).block();
        assertThat(saved.key()).isNull();
        assertThat(saved.partition()).isNull();
        assertThat(saved.errorMessage()).isNull();
    }

    @Test
    void findsByKeyAndStatusAndMarksResolved() {
        var repo = deadLetters();
        var a = repo.save(DeadLetter.failed("resume.analysis", "42", 0, 1L, "{}", new RuntimeException("x"), 3)).block();
        var b = repo.save(DeadLetter.failed("resume.analysis", "42", 0, 2L, "{}", new RuntimeException("y"), 3)).block();
        repo.save(DeadLetter.failed("resume.analysis", "99", 0, 3L, "{}", new RuntimeException("z"), 3)).block();
        repo.save(DeadLetter.failed("email.send", "42", 0, 1L, "{}", new RuntimeException("w"), 3)).block();

        assertThat(repo.findByKey("resume.analysis", "42").collectList().block()).extracting(DeadLetter::id)
                .containsExactly(b.id(), a.id());
        assertThat(repo.findByKey("resume.analysis", "nada").collectList().block()).isEmpty();

        var resolved = repo.markResolved(a.id()).block();
        assertThat(resolved.status()).isEqualTo(DeadLetterStatus.RESOLVED);
        assertThat(resolved.resolvedAt()).isNotNull();
        assertThat(repo.findAll(DeadLetterStatus.RESOLVED).collectList().block()).extracting(DeadLetter::id)
                .containsExactly(a.id());
        assertThat(repo.findAll(DeadLetterStatus.FAILED).collectList().block()).hasSize(3);
        assertThat(repo.findAll(null).collectList().block()).hasSize(4);
        assertThatThrownBy(() -> repo.markResolved(-1L).block()).isInstanceOf(DomainException.NotFound.class);
    }

    @Test
    void profilePhotoIsSavedReplacedFoundAndDeletedWithTheUser() {
        var user = newUser(email(), "55566677788");
        var photos = new ProfilePhotoRepositoryAdapter(executor(), Clock.systemUTC());
        assertThat(photos.exists(user.id()).block()).isFalse();
        assertThat(photos.find(user.id()).block()).isNull();

        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        photos.save(user.id(), new ProfilePhoto(jpeg, "image/jpeg")).block();
        var found = photos.find(user.id()).block();
        assertThat(found.content()).isEqualTo(jpeg);
        assertThat(found.contentType()).isEqualTo("image/jpeg");

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 9, 9};
        photos.save(user.id(), new ProfilePhoto(png, "image/png")).block(); // troca, sem duplicar
        assertThat(photos.find(user.id()).block().content()).isEqualTo(png);
        assertThat(executor().query("SELECT COUNT(*) AS n FROM user_photos", Params.of()).next().block().longValue("n")).isEqualTo(1L);

        users.deleteByPublicId(user.publicId()).block();
        assertThat(photos.exists(user.id()).block()).isFalse();

        assertThatThrownBy(() -> photos.save(-1L, new ProfilePhoto(jpeg, "image/jpeg")).block())
                .isInstanceOf(DomainException.NotFound.class);
        var other = newUser(email(), "11100099988");
        photos.save(other.id(), new ProfilePhoto(jpeg, "image/jpeg")).block();
        photos.delete(other.id()).block();
        assertThat(photos.exists(other.id()).block()).isFalse();
    }

    @Test
    void vacancyKeepsOptionalFieldsAndOnlyTheDescriptionIsRequired() {
        init();
        var full = vacancies.save(new Vacancy(null, "Dev " + UUID.randomUUID(), "Descrição", "Java e SQL", "Criar APIs", null, null, false)).block();
        assertThat(full.requirements()).isEqualTo("Java e SQL");
        assertThat(full.responsibilities()).isEqualTo("Criar APIs");
        assertThat(full.hasPhoto()).isFalse();
        assertThat(vacancies.findById(full.id()).block()).isEqualTo(full);

        var bare = vacancies.save(new Vacancy(null, "QA " + UUID.randomUUID(), "Só descrição", null, null)).block();
        assertThat(bare.requirements()).isNull();
        assertThat(bare.responsibilities()).isNull();

        // editar troca e também apaga os opcionais (null)
        var edited = vacancies.save(new Vacancy(full.id(), full.name(), "Nova", null, "Só isto", null, full.createdAt(), false)).block();
        assertThat(edited.description()).isEqualTo("Nova");
        assertThat(edited.requirements()).isNull();
        assertThat(edited.responsibilities()).isEqualTo("Só isto");
        assertThat(vacancies.findAll().collectList().block()).extracting(Vacancy::id).contains(full.id(), bare.id());
    }

    @Test
    void vacancyStoresStagesTheDeadlineAndCountsApplicants() {
        User user = newUser(email(), "66666666666");
        var deadline = java.time.Instant.parse("2031-12-31T23:59:59Z");
        var saved = vacancies.save(new Vacancy(null, "Prazo " + UUID.randomUUID(), "d", null, null, null, null, false,
                java.util.List.of("Triagem", "Teste técnico"), deadline, 0)).block();
        assertThat(saved.stages()).containsExactly("Triagem", "Teste técnico");
        assertThat(saved.expiresAt()).isEqualTo(deadline);
        assertThat(saved.applicants()).isZero();

        resumes.create(new Resume(null, user.id(), saved.id(), "cv.pdf", "application/pdf", ResumeStatus.PENDING, null,
                null), new byte[] {1}).block();
        assertThat(vacancies.findById(saved.id()).block().applicants()).isEqualTo(1);
        assertThat(vacancies.findAll().collectList().block()).filteredOn(v -> v.id().equals(saved.id()))
                .extracting(Vacancy::applicants).containsExactly(1);

        var cleared = vacancies.save(new Vacancy(saved.id(), saved.name(), "d", null, null, null, saved.createdAt(), false,
                saved.stages(), null, 0)).block();
        assertThat(cleared.expiresAt()).isNull();
        assertThat(cleared.applicants()).isEqualTo(1);

        var plain = vacancies.save(new Vacancy(null, "Padrão " + UUID.randomUUID(), "d", null, null)).block();
        assertThat(plain.stages()).containsExactly("Triagem", "Entrevista", "Contratado");
        assertThat(plain.expiresAt()).isNull();
    }

    @Test
    void vacancyPhotoIsSavedReplacedReportedInTheVacancyAndDeletedWithIt() {
        init();
        var photos = new VacancyPhotoRepositoryAdapter(executor(), Clock.systemUTC());
        var vacancy = vacancies.save(new Vacancy(null, "Foto " + UUID.randomUUID(), "d", null, null)).block();
        assertThat(vacancy.hasPhoto()).isFalse();
        assertThat(photos.find(vacancy.id()).block()).isNull();

        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        photos.save(vacancy.id(), new ProfilePhoto(jpeg, "image/jpeg")).block();
        assertThat(photos.find(vacancy.id()).block().content()).isEqualTo(jpeg);
        assertThat(vacancies.findById(vacancy.id()).block().hasPhoto()).isTrue(); // vem da subconsulta, na leitura única e na lista
        assertThat(vacancies.findAll().collectList().block()).filteredOn(v -> v.id().equals(vacancy.id()))
                .allMatch(Vacancy::hasPhoto);

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 9, 9};
        photos.save(vacancy.id(), new ProfilePhoto(png, "image/png")).block(); // troca sem duplicar
        assertThat(photos.find(vacancy.id()).block().contentType()).isEqualTo("image/png");
        assertThat(executor().query("SELECT COUNT(*) AS n FROM vacancy_photos", Params.of()).next().block().longValue("n")).isEqualTo(1L);

        // editar a vaga não perde a foto
        vacancies.save(new Vacancy(vacancy.id(), vacancy.name(), "outra", null, null, null, vacancy.createdAt(), false)).block();
        assertThat(vacancies.findById(vacancy.id()).block().hasPhoto()).isTrue();

        photos.delete(vacancy.id()).block();
        assertThat(vacancies.findById(vacancy.id()).block().hasPhoto()).isFalse();
        photos.save(vacancy.id(), new ProfilePhoto(jpeg, "image/jpeg")).block();
        vacancies.deleteById(vacancy.id()).block(); // cascata
        assertThat(photos.find(vacancy.id()).block()).isNull();
        assertThatThrownBy(() -> photos.save(-1L, new ProfilePhoto(jpeg, "image/jpeg")).block())
                .isInstanceOf(DomainException.NotFound.class);
    }

    @Test
    void anExistingDatabaseWithoutTheNewColumnsIsMigratedKeepingItsData() {
        init();
        var dialect = executor() instanceof PostgresSqlExecutor ? br.rhhub.infra.database.table.Dialect.POSTGRESQL
                : br.rhhub.infra.database.table.Dialect.SQLITE;
        // como estava em produção antes: sem requirements e responsabilities
        executor().update("ALTER TABLE vacancies DROP COLUMN requirements", Params.of()).block();
        executor().update("ALTER TABLE vacancies DROP COLUMN responsibilities", Params.of()).block();
        executor().update("INSERT INTO vacancies (name, description) VALUES ('Vaga antiga', 'Descrição antiga')", Params.of()).block();

        new br.rhhub.infra.database.table.TableCreator(executor(), dialect).create();
        new br.rhhub.infra.database.table.TableCreator(executor(), dialect).create(); // idempotente

        var old = vacancies.findAll().collectList().block().stream().filter(v -> v.name().equals("Vaga antiga")).findFirst().orElseThrow();
        assertThat(old.description()).isEqualTo("Descrição antiga");
        assertThat(old.requirements()).isNull();
        var saved = vacancies.save(new Vacancy(old.id(), old.name(), old.description(), "Novo requisito", "Nova tarefa", null, old.createdAt(), false)).block();
        assertThat(saved.requirements()).isEqualTo("Novo requisito");
    }
}
