package br.rhhub.infra.redis;

import br.rhhub.application.port.out.RefreshTokenStore;
import br.rhhub.domain.auth.TokenType;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private final RedisStore redis;

    public RedisRefreshTokenStore(RedisStore redis) {
        this.redis = redis;
    }

    private static String key(UUID publicId) {
        return "refresh:" + publicId;
    }

    @Override
    public Mono<Void> save(UUID publicId, String tokenId) {
        return redis.set(key(publicId), tokenId, TokenType.REFRESH.lifetime());
    }

    @Override
    public Mono<Boolean> isCurrent(UUID publicId, String tokenId) {
        return redis.get(key(publicId)).map(current -> current.equals(tokenId)).defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> revoke(UUID publicId) {
        return redis.delete(key(publicId)).then();
    }
}
