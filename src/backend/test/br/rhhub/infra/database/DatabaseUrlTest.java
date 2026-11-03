package br.rhhub.infra.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.infra.database.DatabaseUrl.Kind;
import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

    @Test
    void noneEmptyOrMissingMeansSqlite() {
        for (String raw : new String[] {"none", "NONE", " None ", "", "  ", null, "null"}) {
            var url = DatabaseUrl.parse(raw, "./data/x.db");
            assertThat(url.kind()).as("[%s]", raw).isEqualTo(Kind.SQLITE);
            assertThat(url.sqliteFile().isAbsolute()).isTrue();
            assertThat(url.sqliteFile().toString()).endsWith("data/x.db");
            assertThat(url.sqliteJdbcUrl()).startsWith("jdbc:sqlite:").endsWith("?foreign_keys=on");
        }
    }

    @Test
    void parsesPostgresUrlAndDriverVariants() {
        for (String scheme : new String[] {"postgresql", "postgres", "postgresql+asyncpg", "r2dbc:postgresql",
                "jdbc:postgresql"}) {
            var url = DatabaseUrl.parse(scheme + "://ana:s%40nha%2Fx@db.railway.internal:6543/rhhub", "x");
            assertThat(url.kind()).as(scheme).isEqualTo(Kind.POSTGRESQL);
            assertThat(url.host()).isEqualTo("db.railway.internal");
            assertThat(url.port()).isEqualTo(6543);
            assertThat(url.database()).isEqualTo("rhhub");
            assertThat(url.user()).isEqualTo("ana");
            assertThat(url.password()).isEqualTo("s@nha/x");
        }
    }

    @Test
    void defaultsPortAndReadsSsl() {
        var url = DatabaseUrl.parse("postgresql://u:p@host/db?sslmode=require", "x");
        assertThat(url.port()).isEqualTo(5432);
        assertThat(url.ssl()).isTrue();
    }

    @Test
    void describeNeverLeaksPassword() {
        assertThat(DatabaseUrl.parse("postgresql://u:segredo@h/db", "x").describe()).doesNotContain("segredo");
    }

    @Test
    void rejectsOtherDatabasesAndGarbage() {
        assertThatThrownBy(() -> DatabaseUrl.parse("mysql://u:p@h/db", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DatabaseUrl.parse("lixo", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DatabaseUrl.parse("postgresql://u:p@h", "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
