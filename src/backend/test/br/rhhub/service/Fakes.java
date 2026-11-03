package br.rhhub.service;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailSender;
import br.rhhub.application.port.out.GoogleIdentityProvider;
import br.rhhub.application.port.out.ProfilePhotoRepository;
import br.rhhub.application.port.out.RefreshTokenStore;
import br.rhhub.application.port.out.ResumeAnalysisPublisher;
import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.application.port.out.VacancyPhotoRepository;
import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.application.port.out.VerificationCodeStore;
import br.rhhub.domain.auth.CodePurpose;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.ProfilePhoto;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeFile;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Implementações em memória das portas, para testar os serviços sem banco, Redis nem rede. */
public final class Fakes {

    private Fakes() {
    }

    public static class Users implements UserRepository {
        public final Map<Long, User> byId = new HashMap<>();
        private final AtomicLong seq = new AtomicLong();

        private Mono<User> first(java.util.function.Predicate<User> p) {
            return Mono.justOrEmpty(byId.values().stream().filter(p).findFirst());
        }

        @Override public Mono<User> findById(Long id) { return Mono.justOrEmpty(byId.get(id)); }
        @Override public Mono<User> findByPublicId(UUID id) { return first(u -> u.publicId().equals(id)); }
        @Override public Mono<User> findByEmail(String email) { return first(u -> u.email().equals(email)); }
        @Override public Mono<User> findByGoogleSub(String sub) { return first(u -> sub.equals(u.googleSub())); }
        @Override public Mono<Boolean> existsByEmail(String email) { return findByEmail(email).hasElement(); }
        @Override public Mono<Boolean> existsByCpf(String cpf) { return first(u -> cpf.equals(u.cpf())).hasElement(); }
        @Override public Flux<User> findAll() { return Flux.fromIterable(new ArrayList<>(byId.values())); }

        @Override
        public Mono<User> save(User user) {
            boolean clash = byId.values().stream().anyMatch(o -> !o.publicId().equals(user.publicId())
                    && (o.email().equals(user.email()) || (user.cpf() != null && user.cpf().equals(o.cpf()))));
            if (clash) {
                return Mono.error(new DomainException.Conflict("duplicado"));
            }
            Long id = user.id() == null ? seq.incrementAndGet() : user.id();
            User stored = new User(id, user.publicId(), user.name(), user.email(), user.cpf(), user.age(), user.gender(),
                    user.passwordHash(), user.twoFactor(), user.role(), user.provider(), user.googleSub(),
                    user.createdAt() == null ? Instant.parse("2026-11-03T12:00:00Z") : user.createdAt());
            byId.put(id, stored);
            return Mono.just(stored);
        }

        @Override
        public Mono<Void> deleteByPublicId(UUID publicId) {
            byId.values().removeIf(u -> u.publicId().equals(publicId));
            return Mono.empty();
        }
    }

    public static class Refresh implements RefreshTokenStore {
        public final Map<UUID, String> tokens = new HashMap<>();
        @Override public Mono<Void> save(UUID id, String t) { tokens.put(id, t); return Mono.empty(); }
        @Override public Mono<Boolean> isCurrent(UUID id, String t) { return Mono.just(t.equals(tokens.get(id))); }
        @Override public Mono<Void> revoke(UUID id) { tokens.remove(id); return Mono.empty(); }
    }

    public static class Codes implements VerificationCodeStore {
        public final Map<String, String> issued = new HashMap<>();
        @Override
        public Mono<String> issue(CodePurpose purpose, String email) {
            String code = String.format("%06d", issued.size() + 1);
            issued.put(purpose.key(email), code);
            return Mono.just(code);
        }
        @Override
        public Mono<Void> verify(CodePurpose purpose, String email, String code) {
            String expected = issued.get(purpose.key(email));
            if (expected == null || !expected.equals(code)) {
                return Mono.error(new DomainException.Unauthorized("Código inválido ou expirado."));
            }
            issued.remove(purpose.key(email));
            return Mono.empty();
        }
        public String last(CodePurpose purpose, String email) { return issued.get(purpose.key(email)); }
    }

    public static class Mailbox implements EmailSender {
        public final List<EmailMessage> sent = new ArrayList<>();
        @Override public Mono<Void> send(EmailMessage m) { sent.add(m); return Mono.empty(); }
        @Override public String name() { return "fake"; }
    }

    public static class Google implements GoogleIdentityProvider {
        public Profile profile = new Profile("g-1", "gente@gmail.com", true, "Gente");
        @Override public boolean enabled() { return true; }
        @Override public String authorizationUrl(String state) { return "https://google/auth?state=" + state; }
        @Override public Mono<Profile> fetchProfile(String code) {
            return "ok".equals(code) ? Mono.just(profile) : Mono.error(new DomainException.Unauthorized("recusado"));
        }
    }

    public static class Vacancies implements VacancyRepository {
        public final Map<Long, Vacancy> byId = new HashMap<>();
        /** Como a subconsulta has_photo do banco: a vaga sabe se tem foto pela mesma fonte do VacancyPhotos. */
        public VacancyPhotos photos = new VacancyPhotos();
        private final AtomicLong seq = new AtomicLong();
        private Vacancy view(Vacancy v) { return v.withHasPhoto(photos.byVacancy.containsKey(v.id())); }
        @Override public Mono<Vacancy> findById(Long id) { return Mono.justOrEmpty(byId.get(id)).map(this::view); }
        @Override public Flux<Vacancy> findAll() { return Flux.fromStream(new ArrayList<>(byId.values()).stream().map(this::view)); }
        @Override public Mono<Boolean> existsByName(String name) {
            return Mono.just(byId.values().stream().anyMatch(v -> v.name().equals(name)));
        }
        @Override
        public Mono<Vacancy> save(Vacancy v) {
            if (byId.values().stream().anyMatch(o -> !o.id().equals(v.id()) && o.name().equals(v.name()))) {
                return Mono.error(new DomainException.Conflict("nome repetido"));
            }
            Long id = v.id() == null ? seq.incrementAndGet() : v.id();
            Vacancy stored = new Vacancy(id, v.name(), v.description(), v.requirements(), v.responsibilities(),
                    v.createdBy(), Instant.parse("2026-11-03T12:00:00Z"), false);
            byId.put(id, stored);
            return Mono.just(view(stored));
        }
        @Override public Mono<Void> deleteById(Long id) { byId.remove(id); photos.byVacancy.remove(id); return Mono.empty(); }
    }

    public static class VacancyPhotos implements VacancyPhotoRepository {
        public final Map<Long, ProfilePhoto> byVacancy = new HashMap<>();
        @Override public Mono<Void> save(Long id, ProfilePhoto p) { byVacancy.put(id, p); return Mono.empty(); }
        @Override public Mono<ProfilePhoto> find(Long id) { return Mono.justOrEmpty(byVacancy.get(id)); }
        @Override public Mono<Void> delete(Long id) { byVacancy.remove(id); return Mono.empty(); }
    }

    public static class Resumes implements ResumeRepository {
        public final Map<Long, Resume> byId = new HashMap<>();
        final Map<Long, byte[]> files = new HashMap<>();
        private final AtomicLong seq = new AtomicLong();
        @Override public Mono<Resume> findById(Long id) { return Mono.justOrEmpty(byId.get(id)); }
        @Override public Flux<Resume> findByUserId(Long userId) {
            return Flux.fromStream(byId.values().stream().filter(r -> r.userId().equals(userId)));
        }
        @Override public Flux<Resume> findAll(ResumeStatus status, Long vacancyId) {
            return Flux.fromStream(byId.values().stream().filter(r -> (status == null || r.status() == status)
                    && (vacancyId == null || r.vacancyId().equals(vacancyId))));
        }
        @Override public Mono<ResumeFile> findFile(Long id) {
            Resume r = byId.get(id);
            return r == null ? Mono.empty() : Mono.just(new ResumeFile(files.get(id), r.fileName(), r.contentType()));
        }
        @Override public Mono<Boolean> existsByUserIdAndVacancyId(Long u, Long v) {
            return Mono.just(byId.values().stream().anyMatch(r -> r.userId().equals(u) && r.vacancyId().equals(v)));
        }
        @Override public Mono<Resume> create(Resume r, byte[] file) {
            Long id = seq.incrementAndGet();
            Resume stored = new Resume(id, r.userId(), r.vacancyId(), r.fileName(), r.contentType(), r.status(), r.reason(),
                    Instant.parse("2026-11-03T12:00:00Z"));
            byId.put(id, stored);
            files.put(id, file);
            return Mono.just(stored);
        }
        @Override public Mono<Resume> updateResult(Long id, ResumeStatus status, String reason) {
            Resume r = byId.get(id);
            Resume next = new Resume(r.id(), r.userId(), r.vacancyId(), r.fileName(), r.contentType(), status, reason, r.createdAt());
            byId.put(id, next);
            return Mono.just(next);
        }
        @Override public Mono<Void> deleteById(Long id) { byId.remove(id); files.remove(id); return Mono.empty(); }
    }

    public static class Analysis implements ResumeAnalysisPublisher {
        public final List<Long> queued = new ArrayList<>();
        public boolean failing;
        @Override
        public Mono<Void> publish(Long resumeId) {
            if (failing) {
                return Mono.error(new IllegalStateException("kafka fora do ar"));
            }
            queued.add(resumeId);
            return Mono.empty();
        }
    }

    public static class Photos implements ProfilePhotoRepository {
        public final Map<Long, ProfilePhoto> byUser = new HashMap<>();
        @Override public Mono<Void> save(Long userId, ProfilePhoto p) { byUser.put(userId, p); return Mono.empty(); }
        @Override public Mono<ProfilePhoto> find(Long userId) { return Mono.justOrEmpty(byUser.get(userId)); }
        @Override public Mono<Boolean> exists(Long userId) { return Mono.just(byUser.containsKey(userId)); }
        @Override public Mono<Void> delete(Long userId) { byUser.remove(userId); return Mono.empty(); }
    }
}
