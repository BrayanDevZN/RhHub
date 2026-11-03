package br.rhhub.infrastructure.security;

import br.rhhub.application.port.out.TokenService;
import br.rhhub.application.port.out.TokenType;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.infrastructure.config.AppProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** JWT HS256. O subject é o public_id do usuário e a claim "typ" impede usar um token no lugar de outro. */
@Component
public class JwtTokenService implements TokenService {

    private static final String TYPE_CLAIM = "typ";

    private final SecretKey key;
    private final AppProperties.Jwt jwt;
    private final Clock clock;

    public JwtTokenService(AppProperties props, Clock clock) {
        this.jwt = props.jwt();
        this.key = Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    @Override
    public String issue(TokenType type, UUID publicId) {
        var now = clock.instant();
        return Jwts.builder()
                .subject(publicId.toString())
                .claim(TYPE_CLAIM, type.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl(type))))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public UUID parse(TokenType type, String token) {
        if (token == null || token.isBlank()) {
            throw new DomainException.Unauthorized("Token ausente");
        }
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!type.name().equals(claims.get(TYPE_CLAIM, String.class))) {
                throw new DomainException.Unauthorized("Token de tipo inválido");
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new DomainException.Unauthorized("Token inválido ou expirado", e);
        }
    }

    @Override
    public Duration ttl(TokenType type) {
        return switch (type) {
            case ACCESS -> jwt.accessTtl();
            case REFRESH -> jwt.refreshTtl();
            case TWO_FACTOR -> jwt.twoFactorTtl();
        };
    }
}
