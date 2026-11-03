package br.rhhub.infra.database.table;

import java.util.List;

public class UsersTable implements TableDefinition {

    @Override
    public String name() {
        return "users";
    }

    @Override
    public List<String> statements(Dialect d) {
        return List.of("""
                CREATE TABLE IF NOT EXISTS users (
                    id          %s,
                    public_id   %s NOT NULL,
                    name        %s NOT NULL,
                    email       %s NOT NULL,
                    cpf         %s,
                    age         %s,
                    gender      %s,
                    password    %s,
                    two_factor  %s,
                    role        %s NOT NULL DEFAULT 'user',
                    provider    %s NOT NULL DEFAULT 'local',
                    google_sub  %s,
                    created_at  %s,
                    CONSTRAINT uq_users_public_id UNIQUE (public_id),
                    CONSTRAINT uq_users_email UNIQUE (email),
                    CONSTRAINT uq_users_cpf UNIQUE (cpf),
                    CONSTRAINT uq_users_google_sub UNIQUE (google_sub),
                    CONSTRAINT ck_users_role CHECK (role IN ('user', 'admin')),
                    CONSTRAINT ck_users_provider CHECK (provider IN ('local', 'google')),
                    CONSTRAINT ck_users_gender CHECK (gender IS NULL OR gender IN ('male', 'female', 'other')),
                    CONSTRAINT ck_users_local_has_password CHECK (provider <> 'local' OR password IS NOT NULL)
                )""".formatted(d.id(), d.uuid(), d.varchar(120), d.varchar(255), d.varchar(11), d.integer(),
                d.varchar(10), d.varchar(100), d.boolFalse(), d.varchar(10), d.varchar(10), d.varchar(64),
                d.timestampNow()));
    }
}
