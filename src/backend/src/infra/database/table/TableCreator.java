package br.rhhub.infra.database.table;

import br.rhhub.infra.database.Params;
import br.rhhub.infra.database.SqlExecutor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        this(sql, dialect, List.of(new UsersTable(), new UserPhotosTable(), new VacanciesTable(), new VacancyPhotosTable(), new ResumesTable(),
                new DeadLettersTable(), new NotificationsTable()));
    }

    TableCreator(SqlExecutor sql, Dialect dialect, List<TableDefinition> tables) {
        this.sql = sql;
        this.dialect = dialect;
        this.tables = tables;
    }

    private Mono<Void> addColumnIfMissing(String table, TableDefinition.ColumnAddition addition) {
        String exists = dialect == Dialect.POSTGRESQL
                ? "SELECT 1 AS found FROM information_schema.columns WHERE table_schema = current_schema() "
                        + "AND table_name = :table AND column_name = :column"
                : "SELECT 1 AS found FROM pragma_table_info(:table) WHERE name = :column";
        var params = Params.of().add("table", table).add("column", addition.column());
        return sql.query(exists, params).hasElements().flatMap(present -> {
            if (present) {
                return Mono.<Void>empty();
            }
            log.info("Migração: adicionando a coluna {}.{}", table, addition.column());
            // table, column e definition vêm só das classes *Table (nunca de entrada de usuário)
            return sql.update("ALTER TABLE " + table + " ADD COLUMN " + addition.column() + " " + addition.definition(),
                    Params.of()).then();
        });
    }

    /** Termina quando todas as tabelas existem. Só é bloqueante porque roda uma vez, antes de a aplicação servir. */
    public void create() {
        Flux.fromIterable(tables)
                .concatMap(table -> Flux.fromIterable(table.statements(dialect))
                        .concatMap(statement -> sql.update(statement, Params.of()))
                        .thenMany(Flux.fromIterable(table.newColumns(dialect)))
                        .concatMap(addition -> addColumnIfMissing(table.name(), addition))
                        .then()
                        .doOnSuccess(done -> log.debug("Tabela {} pronta", table.name())))
                .then()
                .block();
        log.info("Tabelas prontas: {}", tables.stream().map(TableDefinition::name).toList());
    }
}
