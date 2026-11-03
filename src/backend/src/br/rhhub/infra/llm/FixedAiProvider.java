package br.rhhub.infra.llm;

import br.rhhub.application.port.out.AiProvider;
import reactor.core.publisher.Mono;

/**
 * Usado com ENVIRONMENT=test: devolve sempre aprovado, no formato "decisão | assunto | HTML"
 * que o prompt do AgentRH pede, sem chamar a rede.
 */
public class FixedAiProvider implements AiProvider {

    public static final String RESPONSE =
            "aproved | Resultado da sua candidatura | <p>Seu currículo foi aprovado (resposta fixa de teste).</p>";

    @Override
    public Mono<String> complete(String systemPrompt, String userInput) {
        return Mono.just(RESPONSE);
    }

    @Override
    public String name() {
        return "fixed";
    }
}
