package br.rhhub.infra.database;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Uma linha do resultado. Converte os tipos que PostgreSQL e SQLite devolvem de jeitos diferentes. */
public final class Row {

    private final Map<String, Object> columns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * Cria a linha copiando as colunas do resultado. O nome da coluna não diferencia maiúsculas de minúsculas.
     */
    public Row(Map<String, Object> source) {
        columns.putAll(source);
    }

    /**
     * Devolve o valor da coluna como veio do banco, sem conversão.
     */
    public Object raw(String column) {
        return columns.get(column);
    }

    /**
     * Lê a coluna como texto.
     *
     * @return o texto, ou nulo se a coluna é nula.
     */
    public String string(String column) {
        Object v = columns.get(column);
        return v == null ? null : v.toString();
    }

    /**
     * Lê a coluna como número inteiro longo (o PostgreSQL e o SQLite devolvem tipos numéricos diferentes).
     *
     * @return o número, ou nulo se a coluna é nula.
     */
    public Long longValue(String column) {
        Object v = columns.get(column);
        return v == null ? null : ((Number) v).longValue();
    }

    /**
     * Lê a coluna como número inteiro.
     *
     * @return o número, ou nulo se a coluna é nula.
     */
    public Integer intValue(String column) {
        Object v = columns.get(column);
        return v == null ? null : ((Number) v).intValue();
    }

    /**
     * Lê a coluna como verdadeiro ou falso. O PostgreSQL devolve boolean e o SQLite devolve 0 ou 1; os dois funcionam. Coluna nula vale falso.
     */
    public boolean bool(String column) {
        Object v = columns.get(column);
        if (v instanceof Boolean b) {
            return b;
        }
        return v instanceof Number n && n.intValue() != 0;
    }

    /**
     * Lê a coluna como UUID. O PostgreSQL devolve UUID e o SQLite devolve texto; os dois funcionam.
     *
     * @return o UUID, ou nulo se a coluna é nula.
     */
    public UUID uuid(String column) {
        Object v = columns.get(column);
        if (v == null) {
            return null;
        }
        return v instanceof UUID u ? u : UUID.fromString(v.toString());
    }

    /**
     * Lê a coluna como instante no tempo. Aceita Instant, OffsetDateTime e Timestamp (PostgreSQL) e texto ISO-8601 (SQLite).
     *
     * @return o instante, ou nulo se a coluna é nula.
     */
    public Instant instant(String column) {
        Object v = columns.get(column);
        if (v == null) {
            return null;
        }
        if (v instanceof Instant i) {
            return i;
        }
        if (v instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        if (v instanceof Timestamp t) {
            return t.toInstant();
        }
        return Instant.parse(v.toString());
    }

    /**
     * Lê a coluna como bytes (arquivo ou imagem). Aceita byte[] e ByteBuffer.
     *
     * @return os bytes, ou nulo se a coluna é nula.
     * @throws IllegalStateException se a coluna não for binária.
     */
    public byte[] bytes(String column) {
        Object v = columns.get(column);
        if (v == null) {
            return null;
        }
        if (v instanceof byte[] b) {
            return b;
        }
        if (v instanceof ByteBuffer buffer) {
            var copy = buffer.duplicate();
            byte[] out = new byte[copy.remaining()];
            copy.get(out);
            return out;
        }
        throw new IllegalStateException("Coluna " + column + " não é binária: " + v.getClass());
    }
}
