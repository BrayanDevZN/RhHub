package br.rhhub.infra.database.table;

import br.rhhub.infra.database.Params;
import br.rhhub.infra.database.SqlExecutor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Cria as tabelas que faltam, na ordem das chaves estrangeiras, na subida da aplicação.
 * É idempotente (IF NOT EXISTS): rodar de novo não mexe em tabela já criada.
 * Não altera tabelas existentes; mudança de colunas pede um comando novo.
 */
public class TableCreator {

    private static final Logger log = LoggerFactory.getLogger(TableCreator.class);

    private final SqlExecutor sql;
    private final Dialect dialect;
    private final List<TableDefinition> tables;

    public TableCreator(SqlExecutor sql, Dialect dialect) {
        this(sql, dialect, List.of(new UsersTable(), new VacanciesTable(), new ResumesTable()));
    }

    TableCreator(SqlExecutor sql, Dialect dialect, List<TableDefinition> tables) {
        this.sql = sql;
        this.dialect = dialect;
        this.tables = tables;
    }

    /** Termina quando todas as tabelas existem. Só é bloqueante porque roda uma vez, antes de a aplicação servir. */
    public void create() {
        Flux.fromIterable(tables)
                .concatMap(table -> Flux.fromIterable(table.statements(dialect))
                        .concatMap(statement -> sql.update(statement, Params.of()))
                        .then()
                        .doOnSuccess(done -> log.debug("Tabela {} pronta", table.name())))
                .then()
                .block();
        log.info("Tabelas prontas: {}", tables.stream().map(TableDefinition::name).toList());
    }
}
