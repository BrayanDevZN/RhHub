-- Mesmo modelo do PostgreSQL, com os tipos do SQLite: UUID e datas em TEXT (ISO-8601),
-- boolean em INTEGER (0/1), arquivo em BLOB. As chaves estrangeiras exigem PRAGMA foreign_keys=ON (já na URL).
CREATE TABLE users (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    public_id   TEXT    NOT NULL,
    name        TEXT    NOT NULL,
    email       TEXT    NOT NULL,
    cpf         TEXT,
    age         INTEGER,
    gender      TEXT,
    password    TEXT,
    two_factor  INTEGER NOT NULL DEFAULT 0,
    role        TEXT    NOT NULL DEFAULT 'user',
    provider    TEXT    NOT NULL DEFAULT 'local',
    google_sub  TEXT,
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    CONSTRAINT uq_users_public_id UNIQUE (public_id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_cpf UNIQUE (cpf),
    CONSTRAINT uq_users_google_sub UNIQUE (google_sub),
    CONSTRAINT ck_users_role CHECK (role IN ('user', 'admin')),
    CONSTRAINT ck_users_provider CHECK (provider IN ('local', 'google')),
    CONSTRAINT ck_users_gender CHECK (gender IS NULL OR gender IN ('male', 'female', 'other')),
    CONSTRAINT ck_users_local_has_password CHECK (provider <> 'local' OR password IS NOT NULL)
);

CREATE TABLE vacancies (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    created_by  INTEGER REFERENCES users (id) ON DELETE SET NULL,
    name        TEXT    NOT NULL,
    description TEXT    NOT NULL,
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    CONSTRAINT uq_vacancies_name UNIQUE (name)
);

CREATE TABLE resumes (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    vacancy_id   INTEGER NOT NULL REFERENCES vacancies (id) ON DELETE CASCADE,
    file         BLOB    NOT NULL,
    file_name    TEXT    NOT NULL,
    content_type TEXT    NOT NULL,
    status       TEXT    NOT NULL DEFAULT 'pending',
    reason       TEXT,
    created_at   TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    CONSTRAINT uq_resumes_user_vacancy UNIQUE (user_id, vacancy_id),
    CONSTRAINT ck_resumes_status CHECK (status IN ('pending', 'aproved', 'recuse'))
);

CREATE INDEX idx_resumes_vacancy ON resumes (vacancy_id);
CREATE INDEX idx_resumes_status ON resumes (status);
