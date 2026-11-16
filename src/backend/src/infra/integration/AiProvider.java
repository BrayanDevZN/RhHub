package br.rhhub.application.port.out;

import reactor.core.publisher.Mono;

/** Porta de saída: pede a resposta de um modelo de IA (analisar currículo). A implementação real fica em infra/llm. */
public interface AiProvider {

    /** Texto bruto da resposta do modelo. Qualquer falha vira erro AiException. */
    Mono<String> complete(String systemPrompt, String userInput);

    /** Nome do provedor ativo, para log (openai, groq, fixed ou none). */
    String name();

    class AiException extends RuntimeException {
        /**
         * Falha ao chamar a IA, com a mensagem.
         */
        public AiException(String message) {
            super(message);
        }

        /**
         * Falha ao chamar a IA, guardando a causa original.
         */
        public AiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
