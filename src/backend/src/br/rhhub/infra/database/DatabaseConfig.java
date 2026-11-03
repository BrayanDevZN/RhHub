package br.rhhub.infra.database;

import br.rhhub.infra.config.AppProperties;
import br.rhhub.infra.database.table.Dialect;
import br.rhhub.infra.database.table.TableCreator;
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

    @Bean(destroyMethod = "close")
    SqlExecutor sqlExecutor(DatabaseUrl url, AppProperties props) {
        return switch (url.kind()) {
            case POSTGRESQL -> new PostgresSqlExecutor(url, props.database().poolSize());
            case SQLITE -> new SqliteSqlExecutor(url);
        };
    }

    /** Cria as tabelas que faltam na subida. Os repositórios dependem dela para só consultar depois disso. */
    @Bean(initMethod = "create")
    TableCreator tableCreator(SqlExecutor sql, DatabaseUrl url) {
        return new TableCreator(sql, Dialect.of(url.kind()));
    }
}
