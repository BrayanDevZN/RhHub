package br.rhhub.infra.database;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

/**
 * DATABASE_URL já interpretada. Aceita postgresql://, postgres://, postgresql+asyncpg://, r2dbc:postgresql://
 * e jdbc:postgresql:// (user e senha na URL, como o Railway entrega). Vazia, ausente ou "none" vira SQLite local.
 */
public record DatabaseUrl(
        Kind kind, String host, int port, String database, String user, String password, boolean ssl, Path sqliteFile) {

    public enum Kind {
        POSTGRESQL,
        SQLITE
    }

    public static DatabaseUrl parse(String raw, String sqlitePath) {
        if (isNone(raw)) {
            return new DatabaseUrl(Kind.SQLITE, null, 0, null, null, null, false, Path.of(sqlitePath).toAbsolutePath());
        }
        String value = raw.strip();
        int at = value.indexOf("://");
        if (at < 0) {
            throw new IllegalArgumentException("DATABASE_URL inválida: esperado postgresql://usuario:senha@host:porta/banco");
        }
        String scheme = value.substring(0, at).toLowerCase(Locale.ROOT);
        if (!(scheme.contains("postgres"))) {
            throw new IllegalArgumentException("DATABASE_URL com banco não suportado: " + scheme.split(":")[0]
                    + " (use postgresql:// ou deixe none para SQLite)");
        }
        URI uri = URI.create("postgresql" + value.substring(at));
        String userInfo = uri.getRawUserInfo();
        String user = null;
        String password = null;
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            user = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
            password = colon < 0 ? null : decode(userInfo.substring(colon + 1));
        }
        String path = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (uri.getHost() == null || path.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL precisa de host e nome do banco");
        }
        String query = uri.getQuery() == null ? "" : uri.getQuery().toLowerCase(Locale.ROOT);
        boolean ssl = query.contains("sslmode=require") || query.contains("sslmode=verify") || query.contains("ssl=true");
        return new DatabaseUrl(Kind.POSTGRESQL, uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 5432, path, user,
                password, ssl, null);
    }

    public static boolean isNone(String raw) {
        return raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw.strip()) || "null".equalsIgnoreCase(raw.strip());
    }

    /** URL JDBC, usada só pelo Flyway na subida. */
    public String jdbcUrl() {
        return switch (kind) {
            case POSTGRESQL -> "jdbc:postgresql://" + host + ":" + port + "/" + database + (ssl ? "?sslmode=require" : "");
            case SQLITE -> "jdbc:sqlite:" + sqliteFile + "?foreign_keys=on";
        };
    }

    /** Para log: nunca inclui a senha. */
    public String describe() {
        return switch (kind) {
            case POSTGRESQL -> "PostgreSQL " + host + ":" + port + "/" + database;
            case SQLITE -> "SQLite " + sqliteFile;
        };
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
