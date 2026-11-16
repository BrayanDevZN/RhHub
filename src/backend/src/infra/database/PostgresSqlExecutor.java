package br.rhhub.infra.database;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.client.SSLMode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** PostgreSQL por R2DBC, com pool de conexões. Nada bloqueia: tudo volta como Mono/Flux. */
public class PostgresSqlExecutor implements SqlExecutor, AutoCloseable {

    private final ConnectionPool pool;
    private final DatabaseClient client;

    /**
     * Abre o PostgreSQL de forma assíncrona (R2DBC) com um pool de conexões.
     *
     * @param poolSize máximo de conexões abertas ao mesmo tempo.
     * O SSL é exigido quando a URL pede (sslmode=require).
     */
    public PostgresSqlExecutor(DatabaseUrl url, int poolSize) {
        var config = PostgresqlConnectionConfiguration.builder()
                .host(url.host())
                .port(url.port())
                .database(url.database())
                .username(url.user() == null ? "postgres" : url.user())
                .password(url.password())
                .sslMode(url.ssl() ? SSLMode.REQUIRE : SSLMode.DISABLE)
                .build();
        this.pool = new ConnectionPool(
                ConnectionPoolConfiguration.builder(new PostgresqlConnectionFactory(config)).maxSize(poolSize).build());
        this.client = DatabaseClient.create(pool);
    }

    /**
     * Executa a consulta sem bloquear nenhuma thread e converte cada linha em {@link Row}. Violações de restrição viram {@link ConstraintViolation}.
     */
    @Override
    public Flux<Row> query(String sql, Params params) {
        return bind(sql, params).fetch().all().map(Row::new).onErrorMap(DataIntegrityViolationException.class,
                e -> new ConstraintViolation(String.valueOf(e.getMostSpecificCause().getMessage()), e));
    }

    /**
     * Executa o comando e devolve quantas linhas mudaram. Violações de restrição viram {@link ConstraintViolation}.
     */
    @Override
    public Mono<Long> update(String sql, Params params) {
        return bind(sql, params).fetch().rowsUpdated().onErrorMap(DataIntegrityViolationException.class,
                e -> new ConstraintViolation(String.valueOf(e.getMostSpecificCause().getMessage()), e));
    }

    /**
     * Liga os parâmetros nomeados à consulta. Valor nulo é ligado com o tipo declarado, porque o driver precisa saber o tipo para enviar NULL.
     */
    private DatabaseClient.GenericExecuteSpec bind(String sql, Params params) {
        DatabaseClient.GenericExecuteSpec spec = client.sql(sql);
        for (var entry : params.values().entrySet()) {
            spec = entry.getValue() == null
                    ? spec.bindNull(entry.getKey(), params.typeOf(entry.getKey()))
                    : spec.bind(entry.getKey(), entry.getValue());
        }
        return spec;
    }

    /**
     * Fecha o pool de conexões ao desligar a aplicação.
     */
    @Override
    public void close() {
        pool.dispose();
    }
}
