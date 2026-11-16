package br.rhhub.infra.llm;

import br.rhhub.application.port.out.AiProvider;
import br.rhhub.infra.config.AppProperties;
import reactor.core.publisher.Mono;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Escolhe o provedor: test usa a resposta fixa; senão OpenAI, depois Groq; sem nenhuma chave, falha ao usar. */
@Configuration
public class LlmConfig {

    @Bean
    AiProvider aiProvider(AppProperties props) {
        var ai = props.ai();
        if (props.isTest()) {
            return new FixedAiProvider();
        }
        if (hasText(ai.openaiKey())) {
            return new OpenAiCompatibleProvider("openai", ai.openaiKey().strip(), ai.openaiModel(),
                    ai.openaiBaseUrl(), ai.timeout());
        }
        if (hasText(ai.groqKey())) {
            return new OpenAiCompatibleProvider("groq", ai.groqKey().strip(), ai.groqModel(),
                    ai.groqBaseUrl(), ai.timeout());
        }
        return new AiProvider() {
            @Override
            public Mono<String> complete(String systemPrompt, String userInput) {
                return Mono.error(new AiException("Nenhuma chave de IA configurada (OPENAI_API_KEY ou GROQ_API_KEY)"));
            }

            @Override
            public String name() {
                return "none";
            }
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
