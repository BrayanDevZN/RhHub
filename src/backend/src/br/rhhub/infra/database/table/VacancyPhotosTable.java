package br.rhhub.infra.database.table;

import java.util.List;

public class VacancyPhotosTable implements TableDefinition {

    @Override
    public String name() {
        return "vacancy_photos";
    }

    /** Uma foto por vaga (a chave é o próprio vacancy_id), fora de vacancies para as listas não trazerem a imagem. */
    @Override
    public List<String> statements(Dialect d) {
        return List.of("""
                CREATE TABLE IF NOT EXISTS vacancy_photos (
                    vacancy_id   %s PRIMARY KEY REFERENCES vacancies (id) ON DELETE CASCADE,
                    content      %s NOT NULL,
                    content_type %s NOT NULL,
                    updated_at   %s,
                    CONSTRAINT ck_vacancy_photos_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'))
                )""".formatted(d.bigint(), d.blob(), d.varchar(20), d.timestampNow()));
    }
}
