package br.rhhub.infrastructure;

import br.rhhub.infrastructure.config.AppProperties;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

public final class TestSupport {

    public static final String SECRET = "segredo-de-teste-com-mais-de-32-bytes!!";

    private TestSupport() {
    }

    public static AppProperties props() {
        return new AppProperties(
                "test",
                new AppProperties.Jwt(SECRET, Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofMinutes(5)),
                new AppProperties.Topics("email.send", "resume.analysis", 3, "rhhub"),
                new AppProperties.Ai("", "m", "http://x", "", "m", "http://x", Duration.ofSeconds(5)),
                new AppProperties.Mail(true, "no-reply@rhhub.test"));
    }

    /** Testes de integração só rodam se o serviço estiver de pé (ex.: Postgres e Redis locais ou no CI). */
    public static boolean reachable(String host, int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean postgresUp() {
        return reachable("localhost", 5432);
    }

    public static boolean redisUp() {
        return reachable("localhost", 6379);
    }
}
