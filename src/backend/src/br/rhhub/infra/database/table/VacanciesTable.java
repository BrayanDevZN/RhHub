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
                    requirements %s,
                    responsibilities %s,
                    stages      %s,
                    expires_at  %s,
                    created_at  %s,
                    CONSTRAINT uq_vacancies_name UNIQUE (name)
                )""".formatted(d.id(), d.bigint(), d.varchar(160), d.text(), d.text(), d.text(), d.text(), d.timestampNullable(), d.timestampNow()));
    }

    /** Requisitos e responsabilidades vieram depois: bancos já criados ganham as colunas na subida. */
    @Override
    public List<ColumnAddition> newColumns(Dialect d) {
        return List.of(new ColumnAddition("requirements", d.text()), new ColumnAddition("responsibilities", d.text()),
                new ColumnAddition("stages", d.text()),
                new ColumnAddition("expires_at", d.timestampNullable()));
    }
}
