package br.rhhub.application.port.out;

import java.time.Duration;

public interface RateLimiter {

    /** Conta uma requisição na janela. Devolve false quando passou do limite. */
    boolean tryAcquire(String key, int limit, Duration window);
}
