package br.rhhub.infra.database.table;

import java.util.List;

/**
 * Fila morta (DLQ): guarda o job que esgotou as tentativas, com a mensagem original, o erro e de onde veio
 * no Kafka. Não tem chave estrangeira de propósito: a chave da mensagem é o id da candidatura, mas a candidatura
 * pode ter sido apagada e o registro da falha deve continuar.
 */
public class DeadLettersTable implements TableDefinition {

    @Override
    public String name() {
        return "dead_letters";
    }

    /** (topic, kafka_partition, kafka_offset) é único: a mesma mensagem morta não é gravada duas vezes. */
    @Override
    public List<String> statements(Dialect d) {
        return List.of("""
                CREATE TABLE IF NOT EXISTS dead_letters (
                    id              %s,
                    topic           %s NOT NULL,
                    message_key     %s,
                    kafka_partition %s,
                    kafka_offset    %s,
                    payload         %s NOT NULL,
                    error_type      %s NOT NULL,
                    error_message   %s,
                    attempts        %s NOT NULL DEFAULT 1,
                    status          %s NOT NULL DEFAULT 'failed',
                    created_at      %s,
                    resolved_at     %s,
                    CONSTRAINT uq_dead_letters_origin UNIQUE (topic, kafka_partition, kafka_offset),
                    CONSTRAINT ck_dead_letters_status CHECK (status IN ('failed', 'resolved'))
                )""".formatted(d.id(), d.varchar(120), d.varchar(255), d.integer(), d.bigint(), d.text(),
                d.varchar(255), d.text(), d.integer(), d.varchar(10), d.timestampNow(), d.timestampNullable()),
                "CREATE INDEX IF NOT EXISTS idx_dead_letters_key ON dead_letters (topic, message_key)",
                "CREATE INDEX IF NOT EXISTS idx_dead_letters_status ON dead_letters (status)");
    }
}
