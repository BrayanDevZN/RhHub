package br.rhhub.infra.database.table;

import java.util.List;

/** Uma tabela do banco: quem sabe criá-la (e seus índices) em qualquer dos dois bancos. */
public interface TableDefinition {

    String name();

    /** Comandos idempotentes (IF NOT EXISTS), na ordem em que devem rodar. */
    List<String> statements(Dialect dialect);
}
