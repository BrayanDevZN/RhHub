package br.rhhub.infra.database.table;

import java.util.List;

public class VacanciesTable implements TableDefinition {

    @Override
    public String name() {
        return "vacancies";
    }

    /** created_by fica nulo se o admin que criou a vaga for excluído; a vaga e as candidaturas permanecem. */
    @Override
    public List<String> statements(Dialect d) {
        return List.of("""
                CREATE TABLE IF NOT EXISTS vacancies (
                    id          %s,
                    created_by  %s REFERENCES users (id) ON DELETE SET NULL,
                    name        %s NOT NULL,
                    description %s NOT NULL,
                    created_at  %s,
                    CONSTRAINT uq_vacancies_name UNIQUE (name)
                )""".formatted(d.id(), d.bigint(), d.varchar(160), d.text(), d.timestampNow()));
    }
}
