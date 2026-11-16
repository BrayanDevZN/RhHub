package br.rhhub.infra.messaging;

import java.util.ArrayList;
import java.util.List;

/**
 * KAFKA_URL já interpretada. Vazia, ausente ou "none" vira o Kafka local (localhost:9092).
 * Aceita host, host:porta, kafka://host:porta, PLAINTEXT://host:porta e uma lista separada por vírgula.
 */
public record KafkaUrl(String bootstrapServers) {

    public static final int DEFAULT_PORT = 9092;
    public static final String LOCAL = "localhost:" + DEFAULT_PORT;

    public static KafkaUrl parse(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw.strip()) || "null".equalsIgnoreCase(raw.strip())) {
            return new KafkaUrl(LOCAL);
        }
        List<String> servers = new ArrayList<>();
        for (String part : raw.split(",")) {
            String address = part.strip().replaceFirst("^[A-Za-z_+]+://", "").replaceAll("/+$", "");
            if (address.isEmpty()) {
                continue;
            }
            int colon = address.lastIndexOf(':');
            String host = colon < 0 ? address : address.substring(0, colon);
            int port = DEFAULT_PORT;
            if (colon >= 0) {
                try {
                    port = Integer.parseInt(address.substring(colon + 1));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("KAFKA_URL com porta inválida: " + part.strip());
                }
            }
            if (host.isBlank() || port < 1 || port > 65535) {
                throw new IllegalArgumentException("KAFKA_URL inválida: " + part.strip());
            }
            servers.add(host + ":" + port);
        }
        if (servers.isEmpty()) {
            throw new IllegalArgumentException("KAFKA_URL inválida: " + raw);
        }
        return new KafkaUrl(String.join(",", servers));
    }
}
