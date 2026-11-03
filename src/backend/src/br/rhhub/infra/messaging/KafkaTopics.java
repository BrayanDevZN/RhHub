package br.rhhub.infra.messaging;

/** Nomes dos tópicos, das partições e dos grupos de consumo. Também usado nas anotações dos consumers (SpEL). */
public record KafkaTopics(String email, String resumeAnalysis, int partitions) {

    public static final String DLT_SUFFIX = ".DLT";

    public String emailGroup() {
        return "rhhub-email";
    }

    public String analysisGroup() {
        return "rhhub-analysis";
    }

    public String deadLetterOf(String topic) {
        return topic + DLT_SUFFIX;
    }
}
