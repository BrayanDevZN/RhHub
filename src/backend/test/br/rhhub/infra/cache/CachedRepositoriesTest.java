package br.rhhub.infra.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Notification;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.service.Fakes;
import br.rhhub.testing.UnitTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cache-aside e invalidação de cada decorador, com a origem espionada para contar as idas ao "banco". */
@UnitTest
class CachedRepositoriesTest {

    private MemoryCacheStore store;
    private QueryCache cache;

    @BeforeEach
    void setUp() {
        store = new MemoryCacheStore();
        cache = new QueryCache(store);
    }

    @Test
    void userIdentityIsCachedWithoutCredentialsAndEvictedOnSaveAndDelete() {
        var db = spy(new Fakes.Users());
        var users = new CachedUserRepository(db, cache);
        User saved = users.save(new User(null, UUID.randomUUID(), "Ana", "ana@gmail.com", "12345678901", 30, null,
                "HASH-SECRETO", false, Role.USER, AuthProvider.LOCAL, "google-sub", null)).block();

        var first = users.findIdentityByPublicId(saved.publicId()).block();
        var second = users.findIdentityByPublicId(saved.publicId()).block();
        assertThat(second).isEqualTo(first);
        verify(db, times(1)).findByPublicId(saved.publicId()); // a segunda veio do cache

        assertThat(first.passwordHash()).isNull();
        assertThat(first.googleSub()).isNull();
        assertThat(store.data.values()).noneMatch(v -> v.contains("HASH-SECRETO") || v.contains("google-sub"));

        // alterar o usuário (ex.: virar admin) apaga a entrada: a próxima leitura vê o papel novo
        users.save(saved.withRole(Role.ADMIN)).block();
        assertThat(store.data).isEmpty();
        assertThat(users.findIdentityByPublicId(saved.publicId()).block().role()).isEqualTo(Role.ADMIN);

        users.deleteByPublicId(saved.publicId()).block();
        assertThat(users.findIdentityByPublicId(saved.publicId()).block()).isNull();
    }

    @Test
    void credentialedReadsNeverTouchTheCache() {
        var users = new CachedUserRepository(new Fakes.Users(), cache);
        User saved = users.save(new User(null, UUID.randomUUID(), "Ana", "ana@gmail.com", null, 30, null, "HASH", false,
                Role.USER, AuthProvider.LOCAL, null, null)).block();
        assertThat(users.findByEmail("ana@gmail.com").block().passwordHash()).isEqualTo("HASH"); // com o hash, do banco
        assertThat(users.findByPublicId(saved.publicId()).block().passwordHash()).isEqualTo("HASH");
        assertThat(store.calls).noneMatch(c -> c.startsWith("get") || c.startsWith("set"));
    }

    @Test
    void vacanciesAreCachedAndEveryWriteClearsThemIncludingResumesAndUserDeletion() {
        var vacancyDb = spy(new Fakes.Vacancies());
        var vacancies = new CachedVacancyRepository(vacancyDb, cache);
        var resumes = new CachedResumeRepository(new Fakes.Resumes(), cache);
        Vacancy vacancy = vacancies.save(new Vacancy(null, "Dev", "d", null, null)).block();

        vacancies.findAll().collectList().block();
        vacancies.findAll().collectList().block();
        vacancies.findById(vacancy.id()).block();
        vacancies.findById(vacancy.id()).block();
        verify(vacancyDb, times(1)).findAll();
        verify(vacancyDb, times(1)).findById(vacancy.id());

        // editar apaga a lista e a vaga
        vacancies.save(new Vacancy(vacancy.id(), "Dev Sênior", "d", null, null, null, vacancy.createdAt(), false)).block();
        assertThat(store.data).isEmpty();
        assertThat(vacancies.findById(vacancy.id()).block().name()).isEqualTo("Dev Sênior");

        // candidatura nova ou apagada muda o contador de candidatos: o cache de vagas cai
        assertThat(store.data).isNotEmpty();
        var resume = resumes.create(new Resume(null, 1L, vacancy.id(), "cv.pdf", "application/pdf", ResumeStatus.PENDING,
                null, null), new byte[] {1}).block();
        assertThat(store.data).isEmpty();
        vacancies.findAll().collectList().block();
        resumes.deleteById(resume.id()).block();
        assertThat(store.data).isEmpty();

        // apagar a vaga também
        vacancies.findAll().collectList().block();
        vacancies.deleteById(vacancy.id()).block();
        assertThat(store.data).isEmpty();
        assertThat(vacancies.findById(vacancy.id()).block()).isNull();

        // e apagar um usuário (que leva as candidaturas junto) também
        var users = new CachedUserRepository(new Fakes.Users(), cache);
        User ana = users.save(new User(null, UUID.randomUUID(), "Ana", "ana@gmail.com", null, 30, null, "h", false,
                Role.USER, AuthProvider.LOCAL, null, null)).block();
        vacancies.save(new Vacancy(null, "Outra", "d", null, null)).block();
        vacancies.findAll().collectList().block();
        assertThat(store.data).isNotEmpty();
        users.deleteByPublicId(ana.publicId()).block();
        assertThat(store.data).isEmpty();
    }

    @Test
    void resumeStatusAndStageChangesDoNotInvalidateAnything() {
        var resumes = new CachedResumeRepository(new Fakes.Resumes(), cache);
        var resume = resumes.create(new Resume(null, 1L, 1L, "cv.pdf", "application/pdf", ResumeStatus.PENDING, null, null),
                new byte[] {1}).block();
        store.calls.clear();
        resumes.updateResult(resume.id(), ResumeStatus.APROVED, "ok").block();
        resumes.updateStage(resume.id(), "Entrevista").block();
        assertThat(store.calls).isEmpty();
    }

    @Test
    void notificationsAreCachedPerUserAndClearedByCreateAndRead() {
        var db = spy(new Fakes.Notifications());
        var notifications = new CachedNotificationRepository(db, cache);
        notifications.create(new Notification(null, 7L, "A", "um", null, false, null)).block();

        assertThat(notifications.countUnread(7L).block()).isEqualTo(1);
        assertThat(notifications.countUnread(7L).block()).isEqualTo(1);
        notifications.findByUser(7L, 30).collectList().block();
        notifications.findByUser(7L, 30).collectList().block();
        verify(db, times(1)).countUnread(7L);
        verify(db, times(1)).findByUser(7L, 30);
        assertThat(store.ttls.get(CacheKeys.notificationUnread(7L))).isEqualTo(CacheKeys.SHORT);

        // um aviso novo para o usuário 7 aparece na hora; o de outro usuário não mexe nas entradas do 7
        notifications.create(new Notification(null, 8L, "B", "outro", null, false, null)).block();
        assertThat(notifications.countUnread(7L).block()).isEqualTo(1);
        verify(db, times(1)).countUnread(7L);
        notifications.create(new Notification(null, 7L, "C", "dois", null, false, null)).block();
        assertThat(notifications.countUnread(7L).block()).isEqualTo(2);

        Long id = notifications.findByUser(7L, 30).collectList().block().get(0).id();
        assertThat(notifications.markRead(id, 7L).block()).isTrue();
        assertThat(notifications.countUnread(7L).block()).isEqualTo(1);
        notifications.markAllRead(7L).block();
        assertThat(notifications.countUnread(7L).block()).isZero();
        assertThat(notifications.findByUser(7L, 30).collectList().block()).allMatch(Notification::read);
    }
}
