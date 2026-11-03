package br.rhhub.infra.database;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/** SQLite local (DATABASE_URL=none), num arquivo temporário. */
class SqliteRepositoryTest extends RepositoryContract {

    private static Path dir;
    private static SqliteSqlExecutor executor;

    @BeforeAll
    static void open() throws Exception {
        dir = Files.createTempDirectory("rhhub-sqlite");
        var url = DatabaseUrl.parse("none", dir.resolve("rhhub.db").toString());
        new DatabaseMigrator(url).migrate();
        executor = new SqliteSqlExecutor(url);
    }

    @AfterAll
    static void close() throws Exception {
        executor.close();
        try (var files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Override
    SqlExecutor executor() {
        return executor;
    }
}
