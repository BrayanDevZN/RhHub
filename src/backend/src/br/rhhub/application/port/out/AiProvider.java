package br.rhhub.application.port.out;

import reactor.core.publisher.Mono;

public interface AiProvider {

    /** Texto bruto da resposta do modelo. Qualquer falha vira erro AiException. */
    Mono<String> complete(String systemPrompt, String userInput);

    /** Nome do provedor ativo, para log (openai, groq, fixed ou none). */
    String name();

    class AiException extends RuntimeException {
        public AiException(String message) {
            super(message);
        }

        public AiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
