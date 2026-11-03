package br.rhhub.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.application.port.out.AiProvider;
import br.rhhub.infra.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LlmConfigTest {

    private final LlmConfig config = new LlmConfig();

    private static AppProperties with(String env, String openai, String groq) {
        return new AppProperties(env, new AppProperties.Database("none", "x.db", 1), new AppProperties.Redis("localhost", 6379, null),
                new AppProperties.Ai(openai, "m", "http://localhost:1", groq, "m", "http://localhost:1",
                        Duration.ofSeconds(1)),
                new AppProperties.Email("http://x", "", Duration.ofSeconds(1)));
    }

    @Test
    void testEnvironmentUsesFixedAnswer() {
        AiProvider provider = config.aiProvider(with("test", "k", "k"));
        assertThat(provider.name()).isEqualTo("fixed");
        assertThat(provider.complete("s", "u").block()).startsWith("aproved |");
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
        assertThatThrownBy(() -> provider.complete("s", "u").block()).isInstanceOf(AiProvider.AiException.class);
    }

    @Test
    void unreachableProviderRaisesAiException() {
        AiProvider provider = config.aiProvider(with("production", "k", ""));
        assertThatThrownBy(() -> provider.complete("s", "u").block()).isInstanceOf(AiProvider.AiException.class);
    }
}
