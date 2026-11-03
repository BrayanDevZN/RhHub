package br.rhhub.infra;

import br.rhhub.infra.config.AppProperties;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

public final class TestSupport {

    private TestSupport() {
    }

    public static AppProperties props() {
        return new AppProperties("test",
                new AppProperties.Ai("", "m", "http://x", "", "m", "http://x", Duration.ofSeconds(5)));
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
}
