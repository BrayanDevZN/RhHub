package br.rhhub.application.port.out;

import java.time.Duration;
import java.util.UUID;

public interface TokenService {

    String issue(TokenType type, UUID publicId);

    /** Valida assinatura, expiração e tipo. Lança DomainException.Unauthorized se inválido. */
    UUID parse(TokenType type, String token);

    Duration ttl(TokenType type);
}
