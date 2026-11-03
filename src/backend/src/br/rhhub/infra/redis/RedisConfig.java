package br.rhhub.infra.redis;

import br.rhhub.infra.config.AppProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Conexão assíncrona com o Redis (Lettuce). Lê REDIS_HOST, REDIS_PORT e REDIS_PASSWORD; sem elas usa o Redis local
 * padrão. Sem senha, conecta só com host e porta.
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    LettuceConnectionFactory redisConnectionFactory(AppProperties props) {
        var factory = new LettuceConnectionFactory(standalone(props.redis()),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(5)).build());
        factory.setValidateConnection(false);
        return factory;
    }

    @Bean
    ReactiveStringRedisTemplate reactiveStringRedisTemplate(LettuceConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    /** Só avisa no log se o Redis não responde na subida; a aplicação sobe mesmo assim. */
    @Bean
    ApplicationRunner redisStartupCheck(RedisStore store, AppProperties props) {
        var redis = props.redis();
        return args -> store.ping().timeout(Duration.ofSeconds(3)).subscribe(
                pong -> log.info("Redis conectado em {}:{} ({})", redis.host(), redis.port(),
                        redis.hasPassword() ? "com senha" : "sem senha"),
                error -> log.warn("Redis indisponível em {}:{}: {}", redis.host(), redis.port(), error.getMessage()));
    }

    /** Host e porta sempre; a senha só entra se existir. */
    static RedisStandaloneConfiguration standalone(AppProperties.Redis redis) {
        var config = new RedisStandaloneConfiguration(redis.host(), redis.port());
        if (redis.hasPassword()) {
            config.setPassword(RedisPassword.of(redis.password().strip()));
        }
        return config;
    }
}
