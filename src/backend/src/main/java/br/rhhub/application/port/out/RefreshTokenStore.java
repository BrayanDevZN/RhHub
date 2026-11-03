package br.rhhub.application.port.out;

import java.time.Duration;
import java.util.UUID;

/** Guarda só o refresh vigente de cada usuário. */
public interface RefreshTokenStore {

    void save(UUID publicId, String token, Duration ttl);

    boolean matches(UUID publicId, String token);

    void delete(UUID publicId);
}
