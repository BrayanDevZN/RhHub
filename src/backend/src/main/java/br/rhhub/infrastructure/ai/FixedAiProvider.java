package br.rhhub.infrastructure.ai;

import br.rhhub.application.port.out.AiProvider;

/**
 * Usado com app.environment=test: devolve sempre aprovado, no formato "decisão | assunto | HTML"
 * que o prompt do AgentRH pede, sem chamar a rede.
 */
public class FixedAiProvider implements AiProvider {

    public static final String RESPONSE =
            "aproved | Resultado da sua candidatura | <p>Seu currículo foi aprovado (resposta fixa de teste).</p>";

    @Override
    public String complete(String systemPrompt, String userInput) {
        return RESPONSE;
    }

    @Override
    public String name() {
        return "fixed";
    }
}
