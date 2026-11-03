package br.rhhub.infra.database.table;

import java.util.List;

public class ResumesTable implements TableDefinition {

    @Override
    public String name() {
        return "resumes";
    }

    /** Uma candidatura por usuário e vaga. Apagar a vaga ou o usuário apaga as candidaturas. */
    @Override
    public List<String> statements(Dialect d) {
        return List.of("""
                CREATE TABLE IF NOT EXISTS resumes (
                    id           %s,
                    user_id      %s NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                    vacancy_id   %s NOT NULL REFERENCES vacancies (id) ON DELETE CASCADE,
                    file         %s NOT NULL,
                    file_name    %s NOT NULL,
                    content_type %s NOT NULL,
                    status       %s NOT NULL DEFAULT 'pending',
                    reason       %s,
                    created_at   %s,
                    CONSTRAINT uq_resumes_user_vacancy UNIQUE (user_id, vacancy_id),
                    CONSTRAINT ck_resumes_status CHECK (status IN ('pending', 'aproved', 'recuse'))
                )""".formatted(d.id(), d.bigint(), d.bigint(), d.blob(), d.varchar(255), d.varchar(120),
                d.varchar(10), d.text(), d.timestampNow()),
                "CREATE INDEX IF NOT EXISTS idx_resumes_vacancy ON resumes (vacancy_id)",
                "CREATE INDEX IF NOT EXISTS idx_resumes_status ON resumes (status)");
    }
}
