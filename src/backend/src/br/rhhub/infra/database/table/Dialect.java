package br.rhhub.infra.database.table;

import br.rhhub.infra.database.DatabaseUrl;

/** Os tipos de coluna de cada banco, para as classes de tabela escreverem o DDL uma vez só. */
public enum Dialect {
    POSTGRESQL,
    SQLITE;

    public static Dialect of(DatabaseUrl.Kind kind) {
        return kind == DatabaseUrl.Kind.POSTGRESQL ? POSTGRESQL : SQLITE;
    }

    /** Chave primária numérica gerada pelo banco. */
    public String id() {
        return this == POSTGRESQL ? "BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    /** Inteiro de 64 bits (chaves estrangeiras). */
    public String bigint() {
        return this == POSTGRESQL ? "BIGINT" : "INTEGER";
    }

    public String integer() {
        return "INTEGER";
    }

    public String varchar(int size) {
        return this == POSTGRESQL ? "VARCHAR(" + size + ")" : "TEXT";
    }

    public String text() {
        return "TEXT";
    }

    public String uuid() {
        return this == POSTGRESQL ? "UUID" : "TEXT";
    }

    /** Boolean com valor padrão falso. */
    public String boolFalse() {
        return this == POSTGRESQL ? "BOOLEAN NOT NULL DEFAULT FALSE" : "INTEGER NOT NULL DEFAULT 0";
    }

    /** Data e hora em UTC, preenchida com o agora por padrão. SQLite guarda ISO-8601 em TEXT. */
    public String timestampNow() {
        return this == POSTGRESQL
                ? "TIMESTAMPTZ NOT NULL DEFAULT now()"
                : "TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))";
    }

    public String blob() {
        return this == POSTGRESQL ? "BYTEA" : "BLOB";
    }
}
