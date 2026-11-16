package br.rhhub.domain.exception;

/** Erros de negócio. A camada de apresentação traduz cada subtipo para um código HTTP. */
public abstract class DomainException extends RuntimeException {

    /**
     * Cria o erro de negócio com uma mensagem que pode ser mostrada ao usuário.
     */
    protected DomainException(String message) {
        super(message);
    }

    /**
     * Cria o erro de negócio guardando a causa original (por exemplo, o erro do banco).
     */
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 400 */
    public static final class Invalid extends DomainException {
        /**
         * Dado de entrada inválido (HTTP 400).
         */
        public Invalid(String message) {
            super(message);
        }
    }

    /** 401 */
    public static final class Unauthorized extends DomainException {
        /**
         * Sem sessão válida ou credencial errada (HTTP 401).
         */
        public Unauthorized(String message) {
            super(message);
        }

        /**
         * Igual ao anterior, guardando a causa original.
         */
        public Unauthorized(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 403 */
    public static final class Forbidden extends DomainException {
        /**
         * Logado, mas sem permissão para a ação (HTTP 403).
         */
        public Forbidden(String message) {
            super(message);
        }
    }

    /** 404 */
    public static final class NotFound extends DomainException {
        /**
         * O recurso pedido não existe (HTTP 404).
         */
        public NotFound(String message) {
            super(message);
        }
    }

    /** 409 */
    public static final class Conflict extends DomainException {
        /**
         * Conflito com o que já existe, como e-mail ou CPF repetido (HTTP 409).
         */
        public Conflict(String message) {
            super(message);
        }

        /**
         * Igual ao anterior, guardando a causa original (a violação de unicidade do banco).
         */
        public Conflict(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 422 */
    public static final class Unprocessable extends DomainException {
        /**
         * Dado válido no formato, mas que o sistema não consegue processar, como um arquivo corrompido (HTTP 422).
         */
        public Unprocessable(String message) {
            super(message);
        }

        /**
         * Igual ao anterior, guardando a causa original.
         */
        public Unprocessable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 429 */
    public static final class TooManyRequests extends DomainException {
        /**
         * Limite de requisições ou de tentativas excedido (HTTP 429).
         */
        public TooManyRequests(String message) {
            super(message);
        }
    }
}
