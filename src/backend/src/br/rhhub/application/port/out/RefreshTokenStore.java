package br.rhhub.application.port.out;

import java.util.UUID;
import reactor.core.publisher.Mono;

/** Guarda o refresh token vigente de cada usuário (um por vez), para poder revogá-lo no logout. */
public interface RefreshTokenStore {

    Mono<Void> save(UUID publicId, String tokenId);

    /** true se tokenId é o vigente. */
    Mono<Boolean> isCurrent(UUID publicId, String tokenId);

    Mono<Void> revoke(UUID publicId);
}
