package br.rhhub.infra.redis;

import br.rhhub.application.port.out.VerificationCodeStore;
import br.rhhub.domain.auth.CodeGenerator;
import br.rhhub.domain.auth.CodePurpose;
import br.rhhub.domain.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Códigos de verificação no Redis: validade, 1 por minuto e no máximo 5 tentativas erradas. */
@Component
public class RedisVerificationCodeStore implements VerificationCodeStore {

    static final int MAX_ATTEMPTS = 5;
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);

    private final RedisStore redis;

    /**
     * Cria o guarda de códigos de verificação sobre o Redis.
     */
    public RedisVerificationCodeStore(RedisStore redis) {
        this.redis = redis;
    }

    /**
     * Gera um código de 6 dígitos e o guarda com a validade da finalidade, substituindo o anterior e zerando o contador de erros. Só permite um código por minuto para o mesmo e-mail e finalidade (uma chave ":cooldown" criada só se ainda não existe).
     *
     * @return o código gerado.
     * @throws DomainException.TooManyRequests se outro código foi pedido há menos de um minuto.
     */
    @Override
    public Mono<String> issue(CodePurpose purpose, String email) {
        String key = purpose.key(email);
        String code = CodeGenerator.sixDigits();
        return redis.setIfAbsent(key + ":cooldown", "1", RESEND_COOLDOWN).flatMap(first -> {
            if (!Boolean.TRUE.equals(first)) {
                return Mono.error(new DomainException.TooManyRequests("Aguarde um minuto para pedir outro código."));
            }
            return redis.delete(key + ":attempts")
                    .then(redis.set(key, code, purpose.validFor()))
                    .thenReturn(code);
        });
    }

    /**
     * Confere o código digitado. Certo: apaga o código e o contador, então ele vale uma vez. Errado: soma 1 no contador de tentativas; na 5ª tentativa errada apaga o código e responde 429.
     *
     * A comparação é em tempo constante, para não vazar por quanto tempo demora.
     *
     * @throws DomainException.Unauthorized "Código inválido ou expirado" se errou ou não existe.
     * @throws DomainException.TooManyRequests se chegou a 5 erros.
     */
    @Override
    public Mono<Void> verify(CodePurpose purpose, String email, String code) {
        String key = purpose.key(email);
        return redis.get(key)
                .switchIfEmpty(Mono.error(new DomainException.Unauthorized("Código inválido ou expirado.")))
                .flatMap(expected -> {
                    if (code != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                            code.strip().getBytes(StandardCharsets.UTF_8))) {
                        return redis.delete(key, key + ":attempts").then();
                    }
                    return redis.incrementWithTtl(key + ":attempts", purpose.validFor()).flatMap(total -> {
                        if (total >= MAX_ATTEMPTS) {
                            return redis.delete(key, key + ":attempts").then(Mono.error(
                                    new DomainException.TooManyRequests("Muitas tentativas. Peça um novo código.")));
                        }
                        return Mono.error(new DomainException.Unauthorized("Código inválido ou expirado."));
                    });
                });
    }
}
