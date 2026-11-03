package br.rhhub.infra.database;

import br.rhhub.infra.database.table.Dialect;
import br.rhhub.infra.database.table.TableCreator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIf;

/** PostgreSQL real por R2DBC (banco rhhub_test em localhost:5432); é pulado se ele não estiver de pé. */
@EnabledIf("br.rhhub.infra.TestSupport#postgresUp")
class PostgresRepositoryTest extends RepositoryContract {

    private static PostgresSqlExecutor executor;

    @BeforeAll
    static void open() {
        var url = DatabaseUrl.parse("postgresql://rhhub:rhhub@localhost:5432/rhhub_test", "unused");
        executor = new PostgresSqlExecutor(url, 5);
        new TableCreator(executor, Dialect.POSTGRESQL).create();
    }

    @AfterAll
    static void close() {
        if (executor != null) {
            executor.close();
        }
    }

    @Override
    SqlExecutor executor() {
        return executor;
    }
}
