package br.rhhub.infra.redis;

import br.rhhub.application.port.out.RefreshTokenStore;
import br.rhhub.domain.auth.TokenType;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Guarda no Redis o id do refresh token válido de cada usuário (permite encerrar sessões). */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private final RedisStore redis;

    /**
     * Cria o guarda de refresh tokens sobre o Redis.
     */
    public RedisRefreshTokenStore(RedisStore redis) {
        this.redis = redis;
    }

    /**
     * Chave do Redis do usuário: "refresh:<uuid>". Cada usuário tem um único refresh token válido por vez.
     */
    private static String key(UUID publicId) {
        return "refresh:" + publicId;
    }

    /**
     * Guarda o id do refresh token atual do usuário (validade de 7 dias). Um login novo troca o id e, com isso, invalida o refresh token anterior.
     */
    @Override
    public Mono<Void> save(UUID publicId, String tokenId) {
        return redis.set(key(publicId), tokenId, TokenType.REFRESH.lifetime());
    }

    /**
     * Diz se o id é o do refresh token atual do usuário. Sem registro (logout ou expirado) devolve false.
     */
    @Override
    public Mono<Boolean> isCurrent(UUID publicId, String tokenId) {
        return redis.get(key(publicId)).map(current -> current.equals(tokenId)).defaultIfEmpty(false);
    }

    /**
     * Apaga o refresh token do usuário, encerrando a sessão dele (logout, troca de senha, exclusão da conta).
     */
    @Override
    public Mono<Void> revoke(UUID publicId) {
        return redis.delete(key(publicId)).then();
    }
}
