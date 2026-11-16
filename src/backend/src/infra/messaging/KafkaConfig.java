package br.rhhub.infra.messaging;

import br.rhhub.application.port.out.DeadLetterRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.infra.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;

/**
 * Conexão com o Kafka, tópicos e política de erro.
 *
 * <ul>
 *   <li>KAFKA_URL vazia ou none: Kafka local (localhost:9092).</li>
 *   <li>KAFKA_API_KEY preenchida: SASL_PLAINTEXT/PLAIN com KAFKA_API_USER; vazia: sem autenticação.</li>
 *   <li>Tópicos de análise e de e-mail, cada um com 3 partições, e os respectivos .DLT (também 3, porque a
 *       mensagem morta vai para a mesma partição).</li>
 *   <li>Falhou: novas tentativas com espera crescente e, no fim, dead_letters + .DLT. Mensagem malformada ou e-mail
 *       inválido não adianta repetir: vai direto para a DLQ.</li>
 * </ul>
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_-]+$");

    /** Tentativas extras depois da primeira. O e-mail tem menos para não reenviar à toa. */
    static final int PRODUCER_MAX_BLOCK_MS = 5_000;
    static final int PRODUCER_REQUEST_TIMEOUT_MS = 8_000;
    static final int PRODUCER_MAX_IDLE_MS = 30_000;
    static final int PRODUCER_DELIVERY_TIMEOUT_MS = 12_000; // precisa ser >= request.timeout.ms (idempotência)
    static final int ANALYSIS_RETRIES = 3;
    static final int EMAIL_RETRIES = 2;

    @Bean
    KafkaTopics kafkaTopics(AppProperties props) {
        var kafka = props.kafka();
        return new KafkaTopics(kafka.emailTopic(), kafka.analysisTopic(), kafka.partitions());
    }

    /** Propriedades de conexão comuns a admin, produtor e consumidor. */
    static Map<String, Object> connectionProperties(AppProperties.Kafka kafka) {
        Map<String, Object> props = new HashMap<>();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, KafkaUrl.parse(kafka.url()).bootstrapServers());
        if (kafka.hasApiKey()) {
            String user = kafka.apiUser() == null || kafka.apiUser().isBlank() ? "rhhub" : kafka.apiUser().strip();
            String key = kafka.apiKey().strip();
            // Entram entre aspas no JAAS: só caracteres seguros (a mesma regra do Dockerfile.kafka).
            if (!SAFE.matcher(user).matches() || !SAFE.matcher(key).matches()) {
                throw new IllegalArgumentException("KAFKA_API_USER e KAFKA_API_KEY só podem ter letras, números, '_' e '-'");
            }
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT");
            props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
            props.put(SaslConfigs.SASL_JAAS_CONFIG,
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + user
                            + "\" password=\"" + key + "\";");
        }
        return props;
    }

    @Bean
    KafkaAdmin kafkaAdmin(AppProperties props) {
        Map<String, Object> config = connectionProperties(props.kafka());
        // Sem Kafka no ar a subida não pode travar: o admin desiste logo e a aplicação sobe.
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 8_000);
        var admin = new KafkaAdmin(config);
        admin.setFatalIfBrokerNotAvailable(false);
        admin.setOperationTimeout(8);
        admin.setCloseTimeout(5);
        return admin;
    }

    @Bean
    ProducerFactory<String, String> kafkaProducerFactory(AppProperties props) {
        Map<String, Object> config = connectionProperties(props.kafka());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Limites curtos: o padrão do cliente espera 60 s (max.block.ms) por um Kafka que não responde, e quem envia um
        // currículo ficava esse minuto inteiro olhando a tela. Com o Kafka no ar a publicação leva milissegundos.
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, PRODUCER_MAX_BLOCK_MS);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, PRODUCER_REQUEST_TIMEOUT_MS);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, PRODUCER_DELIVERY_TIMEOUT_MS);
        // Atrás de um TCP Proxy, uma conexão parada pode ser derrubada sem aviso; o produtor só descobriria no próximo
        // envio, depois de esperar o request.timeout. Fechando as ociosas antes, o envio abre uma conexão nova na hora.
        config.put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, PRODUCER_MAX_IDLE_MS);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    ConsumerFactory<String, String> kafkaConsumerFactory(AppProperties props) {
        Map<String, Object> config = connectionProperties(props.kafka());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // A análise chama a LLM (até 60 s por tentativa) e as tentativas esperam entre si.
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600_000);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, CommonErrorHandler kafkaErrorHandler,
            org.springframework.core.env.Environment env) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        // KAFKA_CONSUMERS_ENABLED=false (a imagem da API): o serviço só publica nas filas e não lê nenhuma. Quem
        // consome é o serviço consumer; assim a API não depende do Kafka para subir nem enche o log de reconexões.
        factory.setAutoStartup(consumersEnabled(env));
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    static boolean consumersEnabled(org.springframework.core.env.Environment env) {
        return env.getProperty("app.kafka.consumers-enabled", Boolean.class, true);
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, DeadLetterRepository deadLetters,
            AppProperties props, KafkaTopics topics) {
        Duration interval = props.kafka().retryInterval();
        // A mensagem morta vai para a mesma partição de <topico>.DLT (por isso a DLT tem as mesmas partições).
        // O padrão do Spring Kafka 3 seria "<topico>-dlt", um tópico que não criamos e que o broker não cria sozinho.
        var toTopic = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(topics.deadLetterOf(record.topic()), record.partition()));
        var handler = new DefaultErrorHandler(
                new DeadLetterRecoverer(deadLetters, toTopic, (record, ex) -> attemptsFor(record.topic(), ex, topics)),
                backOff(ANALYSIS_RETRIES, interval));
        handler.setBackOffFunction((record, ex) -> backOff(retriesFor(record.topic(), topics), interval));
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class,
                DomainException.Invalid.class);
        return handler;
    }

    @Bean
    NewTopic emailTopic(KafkaTopics topics) {
        return topic(topics.email(), topics.partitions());
    }

    @Bean
    NewTopic emailDeadLetterTopic(KafkaTopics topics) {
        return topic(topics.deadLetterOf(topics.email()), topics.partitions());
    }

    @Bean
    NewTopic resumeAnalysisTopic(KafkaTopics topics) {
        return topic(topics.resumeAnalysis(), topics.partitions());
    }

    @Bean
    NewTopic resumeAnalysisDeadLetterTopic(KafkaTopics topics) {
        return topic(topics.deadLetterOf(topics.resumeAnalysis()), topics.partitions());
    }

    private static NewTopic topic(String name, int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    static int retriesFor(String topic, KafkaTopics topics) {
        return topic.equals(topics.email()) ? EMAIL_RETRIES : ANALYSIS_RETRIES;
    }

    /** Quantas vezes o job rodou: a primeira mais as repetições, ou 1 se o erro não adianta repetir. */
    static int attemptsFor(String topic, Exception ex, KafkaTopics topics) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        boolean noRetry = cause instanceof JsonProcessingException || cause instanceof IllegalArgumentException
                || cause instanceof DomainException.Invalid;
        return noRetry ? 1 : retriesFor(topic, topics) + 1;
    }

    private static BackOff backOff(int retries, Duration interval) {
        var backOff = new org.springframework.kafka.support.ExponentialBackOffWithMaxRetries(retries);
        backOff.setInitialInterval(Math.max(1, interval.toMillis()));
        backOff.setMultiplier(2.0);
        return backOff;
    }
}
