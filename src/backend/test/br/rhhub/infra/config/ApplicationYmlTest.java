package br.rhhub.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** Os padrões do application.yml (o que roda no Railway quando a variável não é definida). */
class ApplicationYmlTest {

    private static StandardEnvironment env() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();
        var env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME); // sem variáveis da máquina
        env.getPropertySources().addFirst(new PropertiesPropertySource("yml", props));
        return env;
    }

    @Test
    void groqDefaultsToTheGptOssModelThatWasTestedForReal() {
        var env = env();
        assertThat(env.resolvePlaceholders(env.getProperty("app.ai.groq-model"))).isEqualTo("openai/gpt-oss-120b");
    }

    @Test
    void kafkaClientNoiseIsKeptOutOfTheLogsSoRealErrorsDoNotGetDropped() {
        assertThat(env().getProperty("logging.level.org.apache.kafka")).isEqualTo("WARN");
    }
}
