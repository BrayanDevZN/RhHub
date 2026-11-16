package br.rhhub.domain;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.auth.JwtService;
import br.rhhub.domain.auth.TokenType;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.infra.security.BcryptPasswordHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@UnitTest
class AuthDomainTest {

    private static final String SECRET = "um-segredo-de-teste-com-mais-de-32-caracteres";
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private final JwtService jwt = new JwtService(SECRET, new ObjectMapper());

    private static User user(long id, String email, Role role) {
        return new User(id, UUID.randomUUID(), "N", email, null, null, null, null, false, role, AuthProvider.LOCAL, null, NOW);
    }

    @Test
    void tokenRoundTripKeepsClaimsAndLifetimes() {
        UUID id = UUID.randomUUID();
        for (TokenType type : TokenType.values()) {
            var claims = jwt.verify(jwt.issue(id, type, NOW), type, NOW.plusSeconds(1));
            assertThat(claims.subject()).isEqualTo(id);
            assertThat(claims.type()).isEqualTo(type);
            assertThat(claims.expiresAt()).isEqualTo(NOW.plus(type.lifetime()));
        }
        assertThat(TokenType.ACCESS.lifetime()).isEqualTo(Duration.ofMinutes(15));
        assertThat(TokenType.REFRESH.lifetime()).isEqualTo(Duration.ofDays(7));
        assertThat(TokenType.TWO_FACTOR.lifetime()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void tokenIsRejectedWhenExpiredWrongTypeTamperedOrSignedWithAnotherKey() {
        UUID id = UUID.randomUUID();
        String token = jwt.issue(id, TokenType.ACCESS, NOW);
        assertThatThrownBy(() -> jwt.verify(token, TokenType.ACCESS, NOW.plus(TokenType.ACCESS.lifetime())))
                .isInstanceOf(DomainException.Unauthorized.class);
        assertThatThrownBy(() -> jwt.verify(token, TokenType.REFRESH, NOW)).isInstanceOf(DomainException.Unauthorized.class);

        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];
        assertThatThrownBy(() -> jwt.verify(tampered, TokenType.ACCESS, NOW)).isInstanceOf(DomainException.Unauthorized.class);

        var other = new JwtService("outro-segredo-com-mais-de-32-caracteres!!", new ObjectMapper());
        assertThatThrownBy(() -> other.verify(token, TokenType.ACCESS, NOW)).isInstanceOf(DomainException.Unauthorized.class);

        for (String bad : new String[] {"", null, "a.b", "a.b.c", "....", "eyJhbGciOiJub25lIn0.e30."}) {
            assertThatThrownBy(() -> jwt.verify(bad, TokenType.ACCESS, NOW)).isInstanceOf(DomainException.Unauthorized.class);
        }
    }

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> new JwtService("curto", new ObjectMapper())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtService(null, new ObjectMapper())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bcryptHashesAndChecks() {
        var hasher = new BcryptPasswordHasher();
        String hash = hasher.hash("Senha123");
        assertThat(hash).isNotEqualTo("Senha123").startsWith("$2");
        assertThat(hasher.matches("Senha123", hash)).isTrue();
        assertThat(hasher.matches("senha123", hash)).isFalse();
        assertThat(hasher.matches(null, hash)).isFalse();
    }

    @Test
    void policyFollowsThePermissionTable() {
        var policy = new AccessPolicy("Root@Gmail.com");
        var root = user(1, "root@gmail.com", Role.ADMIN);
        var admin = user(2, "adm@gmail.com", Role.ADMIN);
        var candidate = user(3, "cand@gmail.com", Role.USER);
        var other = user(4, "other@gmail.com", Role.USER);

        assertThat(policy.isRoot(root)).isTrue();
        assertThat(policy.isRoot(admin)).isFalse();
        assertThat(policy.isRoot(user(9, "root@gmail.com", Role.USER))).isFalse();

        policy.requireAdmin(admin);
        assertThatThrownBy(() -> policy.requireAdmin(candidate)).isInstanceOf(DomainException.Forbidden.class);

        policy.checkPromote(admin, candidate);
        assertThatThrownBy(() -> policy.checkPromote(candidate, other)).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> policy.checkPromote(admin, root)).isInstanceOf(DomainException.Conflict.class);

        policy.checkDemote(root, admin);
        assertThatThrownBy(() -> policy.checkDemote(admin, root)).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> policy.checkDemote(root, root)).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> policy.checkDemote(root, candidate)).isInstanceOf(DomainException.Forbidden.class);

        policy.checkDelete(candidate, candidate);
        policy.checkDelete(root, candidate);
        assertThatThrownBy(() -> policy.checkDelete(admin, candidate)).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> policy.checkDelete(root, root)).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> new AccessPolicy("").checkDemote(root, admin)).isInstanceOf(DomainException.Forbidden.class);
    }

    @Test
    void resumeLifecycleOnlyGoesFromPendingToFinal() {
        assertThat(ResumeStatus.PENDING.canMoveTo(ResumeStatus.APROVED)).isTrue();
        assertThat(ResumeStatus.PENDING.canMoveTo(ResumeStatus.RECUSE)).isTrue();
        assertThat(ResumeStatus.PENDING.canMoveTo(ResumeStatus.PENDING)).isFalse();
        assertThat(ResumeStatus.APROVED.canMoveTo(ResumeStatus.RECUSE)).isFalse();
        assertThat(ResumeStatus.RECUSE.canMoveTo(ResumeStatus.APROVED)).isFalse();
    }
}
