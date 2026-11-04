package br.rhhub.application.port.out;

import br.rhhub.domain.model.User;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository {

    Mono<User> findById(Long id);

    Mono<User> findByPublicId(UUID publicId);

    /**
     * Quem é a pessoa logada, para consulta (papel, id, nome): <b>sem o hash da senha</b>, então nunca deve ser usada
     * para depois gravar o usuário. É a leitura de toda requisição autenticada, por isso a versão com cache a guarda.
     */
    default Mono<User> findIdentityByPublicId(UUID publicId) {
        return findByPublicId(publicId).map(User::withoutCredentials);
    }

    Mono<User> findByEmail(String email);

    Mono<User> findByGoogleSub(String googleSub);

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByCpf(String cpf);

    Flux<User> findAll();

    /** Insere (id nulo) ou atualiza. Erro DomainException.Conflict se e-mail, CPF ou google_sub já existirem. */
    Mono<User> save(User user);

    Mono<Void> deleteByPublicId(UUID publicId);
}
