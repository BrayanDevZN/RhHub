package br.rhhub.application.port.out;

import br.rhhub.domain.model.User;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository {

    Mono<User> findByPublicId(UUID publicId);

    Mono<User> findByEmail(String email);

    Mono<User> findByGoogleSub(String googleSub);

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByCpf(String cpf);

    Flux<User> findAll();

    /** Insere (id nulo) ou atualiza. Erro DomainException.Conflict se e-mail, CPF ou google_sub já existirem. */
    Mono<User> save(User user);

    Mono<Void> deleteByPublicId(UUID publicId);
}
