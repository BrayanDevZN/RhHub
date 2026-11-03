package br.rhhub.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.application.port.out.TokenType;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.infrastructure.TestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private final JwtTokenService service = new JwtTokenService(TestSupport.props(), Clock.systemUTC());

    @Test
    void roundTripReturnsTheSameUser() {
        UUID id = UUID.randomUUID();
        assertThat(service.parse(TokenType.ACCESS, service.issue(TokenType.ACCESS, id))).isEqualTo(id);
    }

    @Test
    void rejectsTokenOfAnotherType() {
        String refresh = service.issue(TokenType.REFRESH, UUID.randomUUID());
        assertThatThrownBy(() -> service.parse(TokenType.ACCESS, refresh))
                .isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void rejectsTamperedAndEmptyTokens() {
        String token = service.issue(TokenType.ACCESS, UUID.randomUUID());
        assertThatThrownBy(() -> service.parse(TokenType.ACCESS, token + "x"))
                .isInstanceOf(DomainException.Unauthorized.class);
        assertThatThrownBy(() -> service.parse(TokenType.ACCESS, " "))
                .isInstanceOf(DomainException.Unauthorized.class);
        assertThatThrownBy(() -> service.parse(TokenType.ACCESS, null))
                .isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void rejectsExpiredToken() {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        var old = new JwtTokenService(TestSupport.props(), Clock.fixed(past, ZoneOffset.UTC));
        String token = old.issue(TokenType.ACCESS, UUID.randomUUID());
        assertThatThrownBy(() -> service.parse(TokenType.ACCESS, token))
                .isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void ttlFollowsConfig() {
        assertThat(service.ttl(TokenType.ACCESS)).isEqualTo(Duration.ofMinutes(15));
        assertThat(service.ttl(TokenType.REFRESH)).isEqualTo(Duration.ofDays(7));
        assertThat(service.ttl(TokenType.TWO_FACTOR)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new br.rhhub.infrastructure.config.AppProperties.Jwt("curto", null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
