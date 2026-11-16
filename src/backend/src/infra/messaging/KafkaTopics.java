package br.rhhub.infra.messaging;

/** Nomes dos tópicos, das partições e dos grupos de consumo. Também usado nas anotações dos consumers (SpEL). */
public record KafkaTopics(String email, String resumeAnalysis, int partitions) {

    public static final String DLT_SUFFIX = ".DLT";

    /**
     * Nome do grupo de consumidores de e-mail. Réplicas do consumer no mesmo grupo dividem as mensagens, e cada e-mail é processado por uma só.
     */
    public String emailGroup() {
        return "rhhub-email";
    }

    /**
     * Nome do grupo de consumidores da análise de currículos.
     */
    public String analysisGroup() {
        return "rhhub-analysis";
    }

    /**
     * Nome do tópico de mensagens mortas de um tópico: o nome mais ".DLT".
     */
    public String deadLetterOf(String topic) {
        return topic + DLT_SUFFIX;
    }
}
