package br.rhhub.infra.database.table;

import java.util.List;

public class NotificationsTable implements TableDefinition {

    @Override
    public String name() {
        return "notifications";
    }

    /** Apagar o usuário apaga os avisos dele. is_read evita o nome read, que é palavra reservada em alguns bancos. */
    @Override
    public List<String> statements(Dialect d) {
        return List.of("""
                CREATE TABLE IF NOT EXISTS notifications (
                    id         %s,
                    user_id    %s NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                    title      %s NOT NULL,
                    message    %s NOT NULL,
                    link       %s,
                    is_read    %s,
                    created_at %s
                )""".formatted(d.id(), d.bigint(), d.varchar(160), d.text(), d.varchar(255), d.boolFalse(),
                d.timestampNow()),
                "CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications (user_id, created_at)");
    }
}
