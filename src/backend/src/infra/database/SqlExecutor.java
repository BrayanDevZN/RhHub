package br.rhhub.infra.database;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Executa SQL de forma assíncrona. Há duas implementações (PostgreSQL por R2DBC e SQLite em thread própria)
 * e os adapters escrevem o mesmo SQL para as duas: parâmetros nomeados (:nome) e RETURNING.
 */
public interface SqlExecutor {

    /** Executa e devolve as linhas (SELECT ou INSERT/UPDATE ... RETURNING). */
    Flux<Row> query(String sql, Params params);

    /** Executa e devolve quantas linhas mudaram. */
    Mono<Long> update(String sql, Params params);

    /**
     * Violação de UNIQUE ou chave estrangeira. {@code detail} traz o nome da constraint (PostgreSQL) ou
     * tabela.coluna (SQLite); os adapters olham o nome da coluna para montar a mensagem.
     */
    class ConstraintViolation extends RuntimeException {
        private final String detail;

        public ConstraintViolation(String detail, Throwable cause) {
            super(detail, cause);
            this.detail = detail;
        }

        public String detail() {
            return detail;
        }
    }
}
