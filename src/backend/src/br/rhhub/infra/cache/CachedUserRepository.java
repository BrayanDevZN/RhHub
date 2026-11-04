package br.rhhub.infra.cache;

import br.rhhub.application.port.out.UserRepository;
import br.rhhub.domain.model.User;
import br.rhhub.infra.database.adapter.UserRepositoryAdapter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cache-aside só na leitura de identidade (papel, id, nome de quem está logado), que roda em toda requisição
 * autenticada. Nada que tenha o hash da senha vai para o Redis. Gravar ou apagar o usuário apaga a entrada.
 * As demais leituras (por e-mail, Google, CPF) passam direto para o banco.
 */
@Repository
@Primary
@DependsOn("tableCreator")
@ConditionalOnProperty(name = "cache.enabled", havingValue = "true", matchIfMissing = true)
public class CachedUserRepository implements UserRepository {

    private final UserRepository db;
    private final QueryCache cache;

    @Autowired
    public CachedUserRepository(UserRepositoryAdapter db, QueryCache cache) {
        this((UserRepository) db, cache);
    }

    /** Para testes: qualquer implementação da porta serve de origem. */
    public CachedUserRepository(UserRepository db, QueryCache cache) {
        this.db = db;
        this.cache = cache;
    }

    @Override
    public Mono<User> findIdentityByPublicId(UUID publicId) {
        return cache.one(CacheKeys.userIdentity(publicId), CacheKeys.LONG, User.class,
                () -> db.findIdentityByPublicId(publicId));
    }

    @Override
    public Mono<User> save(User user) {
        return db.save(user).flatMap(saved -> cache.evict(CacheKeys.userIdentity(saved.publicId())).thenReturn(saved));
    }

    /** Apagar a conta apaga em cascata as candidaturas e os avisos: o contador de candidatos das vagas muda. */
    @Override
    public Mono<Void> deleteByPublicId(UUID publicId) {
        return db.findByPublicId(publicId)
                .map(User::id)
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty())
                .flatMap(id -> db.deleteByPublicId(publicId)
                        .then(cache.evict(CacheKeys.userIdentity(publicId)))
                        .then(cache.evictPrefix(CacheKeys.VACANCY_PREFIX))
                        .then(id.isPresent() ? cache.evictPrefix(CacheKeys.notificationPrefix(id.get())) : Mono.empty()));
    }

    @Override
    public Mono<User> findById(Long id) {
        return db.findById(id);
    }

    @Override
    public Mono<User> findByPublicId(UUID publicId) {
        return db.findByPublicId(publicId);
    }

    @Override
    public Mono<User> findByEmail(String email) {
        return db.findByEmail(email);
    }

    @Override
    public Mono<User> findByGoogleSub(String googleSub) {
        return db.findByGoogleSub(googleSub);
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return db.existsByEmail(email);
    }

    @Override
    public Mono<Boolean> existsByCpf(String cpf) {
        return db.existsByCpf(cpf);
    }

    @Override
    public Flux<User> findAll() {
        return db.findAll();
    }
}
