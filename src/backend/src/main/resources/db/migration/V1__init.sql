CREATE TABLE users (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL,
    name        VARCHAR(120) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    cpf         VARCHAR(11),
    age         INT,
    gender      VARCHAR(10),
    password    VARCHAR(100),
    two_factor  BOOLEAN      NOT NULL DEFAULT FALSE,
    role        VARCHAR(10)  NOT NULL DEFAULT 'user',
    provider    VARCHAR(10)  NOT NULL DEFAULT 'local',
    google_sub  VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_public_id UNIQUE (public_id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_cpf UNIQUE (cpf),
    CONSTRAINT uq_users_google_sub UNIQUE (google_sub),
    CONSTRAINT ck_users_role CHECK (role IN ('user', 'admin')),
    CONSTRAINT ck_users_provider CHECK (provider IN ('local', 'google')),
    CONSTRAINT ck_users_gender CHECK (gender IS NULL OR gender IN ('male', 'female', 'other')),
    CONSTRAINT ck_users_local_has_password CHECK (provider <> 'local' OR password IS NOT NULL)
);

-- created_by fica nulo se o admin que criou a vaga for excluído; a vaga e as candidaturas permanecem.
CREATE TABLE vacancies (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_by  BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    name        VARCHAR(160) NOT NULL,
    description TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_vacancies_name UNIQUE (name)
);

CREATE TABLE resumes (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    vacancy_id   BIGINT       NOT NULL REFERENCES vacancies (id) ON DELETE CASCADE,
    file         BYTEA        NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'pending',
    reason       TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_resumes_user_vacancy UNIQUE (user_id, vacancy_id),
    CONSTRAINT ck_resumes_status CHECK (status IN ('pending', 'aproved', 'recuse'))
);

CREATE INDEX idx_resumes_vacancy ON resumes (vacancy_id);
CREATE INDEX idx_resumes_status ON resumes (status);
