package br.rhhub.application.port.out;

import reactor.core.publisher.Mono;

/** Porta de saída: limite de requisições por minuto (anti-abuso). Implementado no Redis. */
public interface RateLimiter {

    /** Conta uma requisição em 60 s para a chave. Completa se está dentro do limite; senão TooManyRequests. */
    Mono<Void> check(String key, int limitPerMinute);
}
