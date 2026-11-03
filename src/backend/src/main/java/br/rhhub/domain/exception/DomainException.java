package br.rhhub.domain.exception;

/** Erros de negócio. A camada de apresentação traduz cada subtipo para um código HTTP. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 400 */
    public static final class Invalid extends DomainException {
        public Invalid(String message) {
            super(message);
        }
    }

    /** 401 */
    public static final class Unauthorized extends DomainException {
        public Unauthorized(String message) {
            super(message);
        }

        public Unauthorized(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 403 */
    public static final class Forbidden extends DomainException {
        public Forbidden(String message) {
            super(message);
        }
    }

    /** 404 */
    public static final class NotFound extends DomainException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** 409 */
    public static final class Conflict extends DomainException {
        public Conflict(String message) {
            super(message);
        }

        public Conflict(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 422 */
    public static final class Unprocessable extends DomainException {
        public Unprocessable(String message) {
            super(message);
        }

        public Unprocessable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 429 */
    public static final class TooManyRequests extends DomainException {
        public TooManyRequests(String message) {
            super(message);
        }
    }
}
