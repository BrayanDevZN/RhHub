package br.rhhub.infra.database;

import br.rhhub.infra.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lê DATABASE_URL e escolhe o banco: PostgreSQL (R2DBC) ou, se for none, SQLite local. */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    DatabaseUrl databaseUrl(AppProperties props) {
        var url = DatabaseUrl.parse(props.database().url(), props.database().sqlitePath());
        log.info("Banco de dados: {}", url.describe());
        return url;
    }

    @Bean(initMethod = "migrate")
    DatabaseMigrator databaseMigrator(DatabaseUrl url) {
        return new DatabaseMigrator(url);
    }

    /** Recebe o migrator só para garantir que as migrations rodem antes de qualquer consulta. */
    @Bean(destroyMethod = "close")
    SqlExecutor sqlExecutor(DatabaseUrl url, AppProperties props, DatabaseMigrator migrated) {
        return switch (url.kind()) {
            case POSTGRESQL -> new PostgresSqlExecutor(url, props.database().poolSize());
            case SQLITE -> new SqliteSqlExecutor(url);
        };
    }
}
