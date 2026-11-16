package br.rhhub.infra.database;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parâmetros nomeados de uma consulta. O tipo só importa quando o valor é nulo (o R2DBC precisa dele). */
public final class Params {

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Class<?>> types = new LinkedHashMap<>();

    /**
     * Use {@link #of()} para criar.
     */
    private Params() {
    }

    /**
     * Cria um conjunto de parâmetros vazio, para encadear com {@code add(...)}.
     */
    public static Params of() {
        return new Params();
    }

    /**
     * Adiciona um parâmetro nomeado informando o tipo. O tipo só importa quando o valor é nulo, porque o driver precisa dele para enviar NULL.
     *
     * @return este mesmo objeto, para encadear.
     */
    public Params add(String name, Object value, Class<?> type) {
        values.put(name, value);
        types.put(name, type);
        return this;
    }

    /**
     * Adiciona um parâmetro nomeado e deduz o tipo do próprio valor.
     *
     * @throws IllegalArgumentException se o valor for nulo (use a versão com tipo).
     */
    public Params add(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Parâmetro nulo precisa de tipo: " + name);
        }
        return add(name, value, value.getClass());
    }

    /**
     * Todos os valores, na ordem em que foram adicionados.
     */
    public Map<String, Object> values() {
        return values;
    }

    /**
     * Tipo declarado de um parâmetro (usado ao ligar valores nulos).
     */
    public Class<?> typeOf(String name) {
        return types.get(name);
    }
}
