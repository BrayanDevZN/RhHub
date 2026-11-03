package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.UserRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Gender;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.infra.database.Params;
import br.rhhub.infra.database.Row;
import br.rhhub.infra.database.SqlExecutor;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private static final String COLUMNS =
            "id, public_id, name, email, cpf, age, gender, password, two_factor, role, provider, google_sub, created_at";

    private final SqlExecutor sql;
    private final Clock clock;

    public UserRepositoryAdapter(SqlExecutor sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    @Override
    public Mono<User> findByPublicId(UUID publicId) {
        return one("public_id = :v", Params.of().add("v", publicId));
    }

    @Override
    public Mono<User> findByEmail(String email) {
        return one("email = :v", Params.of().add("v", email));
    }

    @Override
    public Mono<User> findByGoogleSub(String googleSub) {
        return one("google_sub = :v", Params.of().add("v", googleSub));
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return exists("email = :v", Params.of().add("v", email));
    }

    @Override
    public Mono<Boolean> existsByCpf(String cpf) {
        return exists("cpf = :v", Params.of().add("v", cpf));
    }

    @Override
    public Flux<User> findAll() {
        return sql.query("SELECT " + COLUMNS + " FROM users ORDER BY id", Params.of()).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    public Mono<User> save(User user) {
        var params = Params.of()
                .add("name", user.name())
                .add("email", user.email())
                .add("cpf", user.cpf(), String.class)
                .add("age", user.age(), Integer.class)
                .add("gender", user.gender() == null ? null : user.gender().value(), String.class)
                .add("password", user.passwordHash(), String.class)
                .add("two_factor", user.twoFactor())
                .add("role", user.role().value())
                .add("provider", user.provider().value())
                .add("google_sub", user.googleSub(), String.class);
        Flux<Row> result;
        if (user.id() == null) {
            params.add("public_id", user.publicId() != null ? user.publicId() : UUID.randomUUID())
                    .add("created_at", user.createdAt() != null ? user.createdAt() : clock.instant());
            result = sql.query("""
                    INSERT INTO users (public_id, name, email, cpf, age, gender, password, two_factor, role, provider,
                                       google_sub, created_at)
                    VALUES (:public_id, :name, :email, :cpf, :age, :gender, :password, :two_factor, :role, :provider,
                            :google_sub, :created_at)
                    RETURNING\s""" + COLUMNS, params);
        } else {
            params.add("id", user.id());
            result = sql.query("""
                    UPDATE users SET name = :name, email = :email, cpf = :cpf, age = :age, gender = :gender,
                                     password = :password, two_factor = :two_factor, role = :role,
                                     provider = :provider, google_sub = :google_sub
                    WHERE id = :id RETURNING\s""" + COLUMNS, params);
        }
        return result.next()
                .map(UserRepositoryAdapter::toDomain)
                .switchIfEmpty(Mono.error(new DomainException.NotFound("Usuário não encontrado")))
                .onErrorMap(SqlExecutor.ConstraintViolation.class,
                        e -> new DomainException.Conflict(conflictMessage(e.detail()), e));
    }

    @Override
    public Mono<Void> deleteByPublicId(UUID publicId) {
        return sql.update("DELETE FROM users WHERE public_id = :v", Params.of().add("v", publicId)).then();
    }

    private Mono<User> one(String where, Params params) {
        return sql.query("SELECT " + COLUMNS + " FROM users WHERE " + where, params).next()
                .map(UserRepositoryAdapter::toDomain);
    }

    private Mono<Boolean> exists(String where, Params params) {
        return sql.query("SELECT 1 AS found FROM users WHERE " + where + " LIMIT 1", params).hasElements();
    }

    static User toDomain(Row r) {
        return new User(
                r.longValue("id"),
                r.uuid("public_id"),
                r.string("name"),
                r.string("email"),
                r.string("cpf"),
                r.intValue("age"),
                r.string("gender") == null ? null : Gender.from(r.string("gender")),
                r.string("password"),
                r.bool("two_factor"),
                Role.from(r.string("role")),
                AuthProvider.from(r.string("provider")),
                r.string("google_sub"),
                r.instant("created_at"));
    }

    /** PostgreSQL informa o nome da constraint (uq_users_email); SQLite, tabela.coluna (users.email). */
    static String conflictMessage(String detail) {
        if (detail.contains("email")) {
            return "E-mail já cadastrado";
        }
        if (detail.contains("cpf")) {
            return "CPF já cadastrado";
        }
        if (detail.contains("google_sub")) {
            return "Conta Google já vinculada";
        }
        return "Usuário já existe";
    }
}
