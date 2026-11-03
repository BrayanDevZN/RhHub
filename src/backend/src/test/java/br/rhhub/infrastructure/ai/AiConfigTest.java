package br.rhhub.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.application.port.out.AiProvider;
import br.rhhub.infrastructure.TestSupport;
import br.rhhub.infrastructure.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiConfigTest {

    private final AiConfig config = new AiConfig();

    private static AppProperties with(String env, String openai, String groq) {
        var base = TestSupport.props();
        return new AppProperties(env, base.jwt(), base.topics(),
                new AppProperties.Ai(openai, "m", "http://localhost:1", groq, "m", "http://localhost:1",
                        Duration.ofSeconds(1)),
                base.mail());
    }

    @Test
    void testEnvironmentUsesFixedAnswer() {
        AiProvider provider = config.aiProvider(with("test", "k", "k"));
        assertThat(provider.name()).isEqualTo("fixed");
        assertThat(provider.complete("s", "u")).startsWith("aproved |");
    }

    @Test
    void prefersOpenAiThenGroq() {
        assertThat(config.aiProvider(with("production", "k", "g")).name()).isEqualTo("openai");
        assertThat(config.aiProvider(with("production", "  ", "g")).name()).isEqualTo("groq");
    }

    @Test
    void withoutKeysFailsOnUse() {
        AiProvider provider = config.aiProvider(with("production", "", null));
        assertThat(provider.name()).isEqualTo("none");
        assertThatThrownBy(() -> provider.complete("s", "u")).isInstanceOf(AiProvider.AiException.class);
    }

    @Test
    void unreachableProviderRaisesAiException() {
        AiProvider provider = config.aiProvider(with("production", "k", ""));
        assertThatThrownBy(() -> provider.complete("s", "u")).isInstanceOf(AiProvider.AiException.class);
    }
}
