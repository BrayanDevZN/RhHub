package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.UserRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Gender;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.infra.database.entity.UserEntity;
import br.rhhub.infra.database.jpa.UserJpaRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;
    private final Clock clock;

    public UserRepositoryAdapter(UserJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByPublicId(UUID publicId) {
        return jpa.findByPublicId(publicId).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByGoogleSub(String googleSub) {
        return jpa.findByGoogleSub(googleSub).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCpf(String cpf) {
        return jpa.existsByCpf(cpf);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return jpa.findAll().stream().map(UserRepositoryAdapter::toDomain).toList();
    }

    @Override
    public User save(User user) {
        try {
            return toDomain(jpa.saveAndFlush(toEntity(user)));
        } catch (DataIntegrityViolationException e) {
            throw new DomainException.Conflict(conflictMessage(e), e);
        }
    }

    @Override
    public void deleteByPublicId(UUID publicId) {
        jpa.deleteByPublicId(publicId);
    }

    private UserEntity toEntity(User u) {
        return new UserEntity(
                u.id(),
                u.publicId() != null ? u.publicId() : UUID.randomUUID(),
                u.name(),
                u.email(),
                u.cpf(),
                u.age(),
                u.gender() != null ? u.gender().value() : null,
                u.passwordHash(),
                u.twoFactor(),
                u.role().value(),
                u.provider().value(),
                u.googleSub(),
                u.createdAt() != null ? u.createdAt() : clock.instant());
    }

    static User toDomain(UserEntity e) {
        return new User(
                e.getId(),
                e.getPublicId(),
                e.getName(),
                e.getEmail(),
                e.getCpf(),
                e.getAge(),
                e.getGender() != null ? Gender.from(e.getGender()) : null,
                e.getPasswordHash(),
                e.isTwoFactor(),
                Role.from(e.getRole()),
                AuthProvider.from(e.getProvider()),
                e.getGoogleSub(),
                e.getCreatedAt());
    }

    private static String conflictMessage(DataIntegrityViolationException e) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage());
        if (detail.contains("uq_users_email")) {
            return "E-mail já cadastrado";
        }
        if (detail.contains("uq_users_cpf")) {
            return "CPF já cadastrado";
        }
        if (detail.contains("uq_users_google_sub")) {
            return "Conta Google já vinculada";
        }
        return "Usuário já existe";
    }
}
