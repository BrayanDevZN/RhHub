package br.rhhub.application.port.out;

public interface AiProvider {

    /** Devolve o texto bruto da resposta do modelo. */
    String complete(String systemPrompt, String userInput);

    /** Nome do provedor ativo, para log (openai, groq ou fixed). */
    String name();

    class AiException extends RuntimeException {
        public AiException(String message, Throwable cause) {
            super(message, cause);
        }

        public AiException(String message) {
            super(message);
        }
    }
}
