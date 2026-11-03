package br.rhhub.infrastructure.cache;

import br.rhhub.application.port.out.RefreshTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Guarda o refresh vigente por usuário; um novo login substitui o anterior e o logout o apaga. */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private final StringRedisTemplate redis;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(UUID publicId, String token, Duration ttl) {
        redis.opsForValue().set(key(publicId), token, ttl);
    }

    @Override
    public boolean matches(UUID publicId, String token) {
        String stored = redis.opsForValue().get(key(publicId));
        if (stored == null || token == null) {
            return false;
        }
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void delete(UUID publicId) {
        redis.delete(key(publicId));
    }

    static String key(UUID publicId) {
        return "refresh_token:" + publicId;
    }
}
