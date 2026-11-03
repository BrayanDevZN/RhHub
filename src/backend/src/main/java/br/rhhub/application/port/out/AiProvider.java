package br.rhhub.application.port.out;

public interface AiProvider {

    /** Devolve o texto bruto da resposta do modelo. Lança AiException em qualquer falha. */
    String complete(String systemPrompt, String userInput);

    /** Nome do provedor ativo, para log (openai, groq ou fixed). */
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
