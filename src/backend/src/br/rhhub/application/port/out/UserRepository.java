package br.rhhub.application.port.out;

import br.rhhub.domain.model.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    List<User> findAll();

    /** Insere (id nulo) ou atualiza. Lança DomainException.Conflict se e-mail, CPF ou google_sub já existirem. */
    User save(User user);

    void deleteByPublicId(UUID publicId);
}
