package br.rhhub.infra;

import br.rhhub.infra.config.AppProperties;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

public final class TestSupport {

    private TestSupport() {
    }

    public static AppProperties props() {
        return new AppProperties("test", new AppProperties.Database("none", "./data/test.db", 5),
                new AppProperties.Redis("localhost", 6379, null),
                new AppProperties.Kafka("none", "rhhub", null, 3, java.time.Duration.ofMillis(50), "email.send", "resume.analysis"),
                new AppProperties.Ai("", "m", "http://x", "", "m", "http://x", Duration.ofSeconds(5)),
                new AppProperties.Email("http://x", "", Duration.ofSeconds(5)));
    }

    /** Testes de integração só rodam se o serviço estiver de pé (ex.: Postgres local ou no CI). */
    public static boolean postgresUp() {
        return reachable(5432);
    }

    public static boolean redisUp() {
        return reachable(6379);
    }

    public static boolean reachable(int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * true quando CADA consumer (cada thread de cada listener) já tem partição. Esperar só o primeiro não basta: o
     * consumer que entra primeiro recebe todas as partições e, quando os outros chegam, o rebalance move partições
     * no meio de um teste. A thread nova começa a contagem de tentativas do zero (o controle de falhas é por thread),
     * e a mesma mensagem é tentada de novo do início.
     */
    public static boolean allConsumersAssigned(org.springframework.kafka.config.KafkaListenerEndpointRegistry registry) {
        var containers = registry.getListenerContainers();
        return !containers.isEmpty() && containers.stream().allMatch(c -> {
            if (c instanceof org.springframework.kafka.listener.ConcurrentMessageListenerContainer<?, ?> concurrent) {
                return concurrent.isRunning() && !concurrent.getContainers().isEmpty()
                        && concurrent.getContainers().stream().allMatch(child -> child.isRunning()
                                && child.getAssignedPartitions() != null && !child.getAssignedPartitions().isEmpty());
            }
            return c.isRunning() && !c.getAssignedPartitions().isEmpty();
        });
    }
}
