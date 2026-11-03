package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.DeadLetterRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.DeadLetter;
import br.rhhub.domain.model.DeadLetterStatus;
import br.rhhub.infra.database.Params;
import br.rhhub.infra.database.Row;
import br.rhhub.infra.database.SqlExecutor;
import java.time.Clock;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@DependsOn("tableCreator")
public class DeadLetterRepositoryAdapter implements DeadLetterRepository {

    private static final String COLUMNS = "id, topic, message_key, kafka_partition, kafka_offset, payload, error_type, "
            + "error_message, attempts, status, created_at, resolved_at";

    private final SqlExecutor sql;
    private final Clock clock;

    public DeadLetterRepositoryAdapter(SqlExecutor sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    @Override
    public Mono<DeadLetter> save(DeadLetter d) {
        var params = Params.of()
                .add("topic", d.topic())
                .add("message_key", d.key(), String.class)
                .add("kafka_partition", d.partition(), Integer.class)
                .add("kafka_offset", d.offset(), Long.class)
                .add("payload", d.payload())
                .add("error_type", d.errorType())
                .add("error_message", d.errorMessage(), String.class)
                .add("attempts", d.attempts())
                .add("status", d.status().value())
                .add("created_at", d.createdAt() != null ? d.createdAt() : clock.instant());
        return sql.query("""
                INSERT INTO dead_letters (topic, message_key, kafka_partition, kafka_offset, payload, error_type,
                                          error_message, attempts, status, created_at)
                VALUES (:topic, :message_key, :kafka_partition, :kafka_offset, :payload, :error_type,
                        :error_message, :attempts, :status, :created_at)
                ON CONFLICT (topic, kafka_partition, kafka_offset) DO NOTHING
                RETURNING\s""" + COLUMNS, params)
                .next()
                .switchIfEmpty(Mono.defer(() -> existing(d)))
                .map(DeadLetterRepositoryAdapter::toDomain);
    }

    /** O insert não gravou nada porque a origem já existe: devolve a que estava lá. */
    private Mono<Row> existing(DeadLetter d) {
        return sql.query("SELECT " + COLUMNS + " FROM dead_letters WHERE topic = :topic "
                + "AND kafka_partition = :kafka_partition AND kafka_offset = :kafka_offset",
                Params.of().add("topic", d.topic()).add("kafka_partition", d.partition(), Integer.class)
                        .add("kafka_offset", d.offset(), Long.class)).next();
    }

    @Override
    public Mono<DeadLetter> findById(Long id) {
        return sql.query("SELECT " + COLUMNS + " FROM dead_letters WHERE id = :id", Params.of().add("id", id)).next()
                .map(DeadLetterRepositoryAdapter::toDomain);
    }

    @Override
    public Flux<DeadLetter> findByKey(String topic, String key) {
        return sql.query("SELECT " + COLUMNS + " FROM dead_letters WHERE topic = :topic AND message_key = :key "
                + "ORDER BY created_at DESC, id DESC", Params.of().add("topic", topic).add("key", key))
                .map(DeadLetterRepositoryAdapter::toDomain);
    }

    @Override
    public Flux<DeadLetter> findAll(DeadLetterStatus status) {
        var query = new StringBuilder("SELECT " + COLUMNS + " FROM dead_letters");
        var params = Params.of();
        if (status != null) {
            query.append(" WHERE status = :status");
            params.add("status", status.value());
        }
        query.append(" ORDER BY created_at DESC, id DESC");
        return sql.query(query.toString(), params).map(DeadLetterRepositoryAdapter::toDomain);
    }

    @Override
    public Mono<DeadLetter> markResolved(Long id) {
        return sql.query("UPDATE dead_letters SET status = :status, resolved_at = :now WHERE id = :id RETURNING " + COLUMNS,
                Params.of().add("id", id).add("status", DeadLetterStatus.RESOLVED.value()).add("now", clock.instant()))
                .next()
                .map(DeadLetterRepositoryAdapter::toDomain)
                .switchIfEmpty(Mono.error(new DomainException.NotFound("Registro da DLQ não encontrado")));
    }

    static DeadLetter toDomain(Row r) {
        return new DeadLetter(r.longValue("id"), r.string("topic"), r.string("message_key"),
                r.intValue("kafka_partition"), r.longValue("kafka_offset"), r.string("payload"), r.string("error_type"),
                r.string("error_message"), r.intValue("attempts"), DeadLetterStatus.from(r.string("status")),
                r.instant("created_at"), r.instant("resolved_at"));
    }
}
