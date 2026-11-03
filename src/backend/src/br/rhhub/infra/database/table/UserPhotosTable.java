package br.rhhub.infra.database.table;

import java.util.List;

public class UserPhotosTable implements TableDefinition {

    @Override
    public String name() {
        return "user_photos";
    }

    /** Uma foto por usuário (a chave é o próprio user_id). Fica fora de users para as consultas de usuário não trazerem a imagem. */
    @Override
    public List<String> statements(Dialect d) {
        return List.of("""
                CREATE TABLE IF NOT EXISTS user_photos (
                    user_id      %s PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
                    content      %s NOT NULL,
                    content_type %s NOT NULL,
                    updated_at   %s,
                    CONSTRAINT ck_user_photos_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))
                )""".formatted(d.bigint(), d.blob(), d.varchar(20), d.timestampNow()));
    }
}
