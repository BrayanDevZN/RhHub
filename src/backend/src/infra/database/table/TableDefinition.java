package br.rhhub.infra.database.table;

import java.util.List;

/** Uma tabela do banco: quem sabe criá-la (e seus índices) em qualquer dos dois bancos. */
public interface TableDefinition {

    String name();

    /** Comandos idempotentes (IF NOT EXISTS), na ordem em que devem rodar. */
    List<String> statements(Dialect dialect);

    /** Coluna acrescentada depois da primeira versão da tabela. definition é o tipo e as restrições (ex.: "TEXT"). */
    record ColumnAddition(String column, String definition) {
    }

    /**
     * Colunas que bancos já criados talvez não tenham. O CREATE TABLE IF NOT EXISTS não altera tabela existente, então
     * o TableCreator confere cada uma e faz o ALTER TABLE só se faltar. Também devem constar no CREATE TABLE.
     */
    default List<ColumnAddition> newColumns(Dialect dialect) {
        return List.of();
    }
}
