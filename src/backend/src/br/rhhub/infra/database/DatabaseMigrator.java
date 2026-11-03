package br.rhhub.infra.database;

import java.io.IOException;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Aplica as migrations (db/migration/postgresql ou db/migration/sqlite) uma vez, na subida. */
public class DatabaseMigrator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrator.class);

    private final DatabaseUrl url;

    public DatabaseMigrator(DatabaseUrl url) {
        this.url = url;
    }

    public void migrate() {
        if (url.kind() == DatabaseUrl.Kind.SQLITE) {
            try {
                Files.createDirectories(url.sqliteFile().getParent());
            } catch (IOException e) {
                throw new IllegalStateException("Não foi possível criar a pasta do SQLite: " + url.sqliteFile(), e);
            }
        }
        String folder = url.kind() == DatabaseUrl.Kind.POSTGRESQL ? "postgresql" : "sqlite";
        var result = Flyway.configure()
                .dataSource(url.jdbcUrl(), url.user(), url.password())
                .locations("classpath:db/migration/" + folder)
                .load()
                .migrate();
        log.info("Banco {}: {} migration(s) aplicada(s)", url.describe(), result.migrationsExecuted);
    }
}
