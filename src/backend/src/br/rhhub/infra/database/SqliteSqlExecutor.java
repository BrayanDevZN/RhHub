package br.rhhub.infra.database;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * SQLite não tem driver R2DBC oficial. Aqui o JDBC roda numa única thread dedicada (como o aiosqlite do Python):
 * o chamador recebe Mono/Flux e nunca bloqueia, e o SQLite, que aceita um escritor por vez, fica serializado.
 */
public class SqliteSqlExecutor implements SqlExecutor, AutoCloseable {

    private final SingleConnectionDataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;
    private final Scheduler scheduler = Schedulers.newSingle("sqlite", true);

    public SqliteSqlExecutor(DatabaseUrl url) {
        try {
            Files.createDirectories(url.sqliteFile().getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar a pasta do SQLite: " + url.sqliteFile(), e);
        }
        this.dataSource = new SingleConnectionDataSource(url.sqliteJdbcUrl(), true);
        this.dataSource.setAutoCommit(true);
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public Flux<Row> query(String sql, Params params) {
        return Mono.fromCallable(() -> jdbc.queryForList(sql, source(params)))
                .subscribeOn(scheduler)
                .onErrorMap(DataAccessException.class, SqliteSqlExecutor::translate)
                .flatMapMany(rows -> Flux.fromIterable(rows).map(Row::new));
    }

    @Override
    public Mono<Long> update(String sql, Params params) {
        return Mono.fromCallable(() -> (long) jdbc.update(sql, source(params)))
                .subscribeOn(scheduler)
                .onErrorMap(DataAccessException.class, SqliteSqlExecutor::translate);
    }

    /** UUID, Instant e Boolean não existem no SQLite: viram TEXT/TEXT/INTEGER. */
    private static MapSqlParameterSource source(Params params) {
        var source = new MapSqlParameterSource();
        for (Map.Entry<String, Object> e : params.values().entrySet()) {
            Object v = e.getValue();
            if (v instanceof UUID || v instanceof Instant) {
                v = v.toString();
            } else if (v instanceof Boolean b) {
                v = b ? 1 : 0;
            }
            source.addValue(e.getKey(), v);
        }
        return source;
    }

    private static Throwable translate(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof SQLException sql && sql.getMessage() != null
                && (sql.getMessage().contains("UNIQUE constraint failed")
                        || sql.getMessage().contains("FOREIGN KEY constraint failed"))) {
            return new ConstraintViolation(sql.getMessage(), e);
        }
        return e;
    }

    @Override
    public void close() {
        scheduler.dispose();
        dataSource.destroy();
    }

    /** Só para testes e diagnóstico. */
    List<Map<String, Object>> rawQuery(String sql) {
        return jdbc.queryForList(sql, Map.of());
    }
}
