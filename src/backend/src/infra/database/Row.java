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

    public Row(Map<String, Object> source) {
        columns.putAll(source);
    }

    public Object raw(String column) {
        return columns.get(column);
    }

    public String string(String column) {
        Object v = columns.get(column);
        return v == null ? null : v.toString();
    }

    public Long longValue(String column) {
        Object v = columns.get(column);
        return v == null ? null : ((Number) v).longValue();
    }

    public Integer intValue(String column) {
        Object v = columns.get(column);
        return v == null ? null : ((Number) v).intValue();
    }

    public boolean bool(String column) {
        Object v = columns.get(column);
        if (v instanceof Boolean b) {
            return b;
        }
        return v instanceof Number n && n.intValue() != 0;
    }

    public UUID uuid(String column) {
        Object v = columns.get(column);
        if (v == null) {
            return null;
        }
        return v instanceof UUID u ? u : UUID.fromString(v.toString());
    }

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
