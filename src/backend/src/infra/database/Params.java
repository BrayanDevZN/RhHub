package br.rhhub.infra.database;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parâmetros nomeados de uma consulta. O tipo só importa quando o valor é nulo (o R2DBC precisa dele). */
public final class Params {

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Class<?>> types = new LinkedHashMap<>();

    private Params() {
    }

    public static Params of() {
        return new Params();
    }

    public Params add(String name, Object value, Class<?> type) {
        values.put(name, value);
        types.put(name, type);
        return this;
    }

    public Params add(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Parâmetro nulo precisa de tipo: " + name);
        }
        return add(name, value, value.getClass());
    }

    public Map<String, Object> values() {
        return values;
    }

    public Class<?> typeOf(String name) {
        return types.get(name);
    }
}
