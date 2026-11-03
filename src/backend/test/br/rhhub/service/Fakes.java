package br.rhhub.service;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailSender;
import br.rhhub.application.port.out.GoogleIdentityProvider;
import br.rhhub.application.port.out.RefreshTokenStore;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.application.port.out.VerificationCodeStore;
import br.rhhub.domain.auth.CodePurpose;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.User;
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
final class Fakes {

    private Fakes() {
    }

    static class Users implements UserRepository {
        final Map<Long, User> byId = new HashMap<>();
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

    static class Refresh implements RefreshTokenStore {
        final Map<UUID, String> tokens = new HashMap<>();
        @Override public Mono<Void> save(UUID id, String t) { tokens.put(id, t); return Mono.empty(); }
        @Override public Mono<Boolean> isCurrent(UUID id, String t) { return Mono.just(t.equals(tokens.get(id))); }
        @Override public Mono<Void> revoke(UUID id) { tokens.remove(id); return Mono.empty(); }
    }

    static class Codes implements VerificationCodeStore {
        final Map<String, String> issued = new HashMap<>();
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
        String last(CodePurpose purpose, String email) { return issued.get(purpose.key(email)); }
    }

    static class Mailbox implements EmailSender {
        final List<EmailMessage> sent = new ArrayList<>();
        @Override public Mono<Void> send(EmailMessage m) { sent.add(m); return Mono.empty(); }
        @Override public String name() { return "fake"; }
    }

    static class Google implements GoogleIdentityProvider {
        Profile profile = new Profile("g-1", "gente@gmail.com", true, "Gente");
        @Override public boolean enabled() { return true; }
        @Override public String authorizationUrl(String state) { return "https://google/auth?state=" + state; }
        @Override public Mono<Profile> fetchProfile(String code) {
            return "ok".equals(code) ? Mono.just(profile) : Mono.error(new DomainException.Unauthorized("recusado"));
        }
    }
}
