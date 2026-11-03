package br.rhhub.infra.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.infra.database.table.Dialect;
import br.rhhub.infra.database.table.DeadLettersTable;
import br.rhhub.infra.database.table.ResumesTable;
import br.rhhub.infra.database.table.TableCreator;
import br.rhhub.infra.database.table.UsersTable;
import br.rhhub.infra.database.table.VacanciesTable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableCreatorTest {

    @Test
    void createsEveryTableAndIndexOnSqliteAndIsIdempotent() throws Exception {
        Path dir = Files.createTempDirectory("rhhub-creator");
        var url = DatabaseUrl.parse("none", dir.resolve("novo/rhhub.db").toString());
        var executor = new SqliteSqlExecutor(url);
        try {
            var creator = new TableCreator(executor, Dialect.SQLITE);
            creator.create();
            creator.create(); // segunda vez não pode falhar nem duplicar

            var names = executor.query("SELECT name FROM sqlite_master WHERE type IN ('table', 'index') "
                    + "AND name NOT LIKE 'sqlite_%'", Params.of()).map(r -> r.string("name")).collectList().block();
            assertThat(names).contains("users", "vacancies", "resumes", "dead_letters", "idx_resumes_vacancy", "idx_resumes_status",
                    "idx_dead_letters_key", "idx_dead_letters_status");
        } finally {
            executor.close();
        }
    }

    @Test
    void everyDialectProducesStatementsForEveryTable() {
        for (Dialect dialect : Dialect.values()) {
            for (var table : List.of(new UsersTable(), new VacanciesTable(), new ResumesTable(), new DeadLettersTable())) {
                assertThat(table.statements(dialect)).first().asString()
                        .startsWith("CREATE TABLE IF NOT EXISTS " + table.name())
                        .doesNotContain("%s");
            }
        }
        assertThat(new UsersTable().statements(Dialect.POSTGRESQL).get(0)).contains("UUID", "TIMESTAMPTZ", "BOOLEAN");
        assertThat(new UsersTable().statements(Dialect.SQLITE).get(0)).contains("AUTOINCREMENT").doesNotContain("UUID");
    }

    @Test
    void constraintsAreEnforcedBySqliteSchema() throws Exception {
        Path dir = Files.createTempDirectory("rhhub-creator");
        var executor = new SqliteSqlExecutor(DatabaseUrl.parse("none", dir.resolve("c.db").toString()));
        try {
            new TableCreator(executor, Dialect.SQLITE).create();
            // role inválido viola o CHECK
            assertThatThrownBy(() -> executor.update("INSERT INTO users (public_id, name, email, password, role) "
                    + "VALUES ('x', 'a', 'a@a', 'h', 'root')", Params.of()).block())
                    .hasMessageContaining("CHECK");
        } finally {
            executor.close();
        }
    }
}
