package br.rhhub.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.domain.auth.CodePurpose;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.infra.TestSupport;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/** Roda contra um Redis em localhost:6379 (e-mails e chaves únicos por teste); é pulado se ele não estiver de pé. */
@EnabledIf("br.rhhub.infra.TestSupport#redisUp")
class AuthStoresIntegrationTest {

    private static LettuceConnectionFactory factory;
    private static RedisStore store;

    @BeforeAll
    static void connect() {
        factory = new RedisConfig().redisConnectionFactory(TestSupport.props());
        factory.afterPropertiesSet();
        store = new RedisStore(new ReactiveStringRedisTemplate(factory));
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    private static String email() {
        return UUID.randomUUID() + "@gmail.com";
    }

    @Test
    void codeIsSixDigitsAndWorksOnce() {
        var codes = new RedisVerificationCodeStore(store);
        String email = email();
        String code = codes.issue(CodePurpose.SIGNUP, email).block();
        assertThat(code).matches("\\d{6}");
        assertThat(store.ttl(CodePurpose.SIGNUP.key(email)).block()).isPositive().isLessThanOrEqualTo(CodePurpose.SIGNUP.validFor());

        codes.verify(CodePurpose.SIGNUP, email, code).block();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> codes.verify(CodePurpose.SIGNUP, email, code).block())
                .isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void codeFromAnotherPurposeDoesNotWork() {
        var codes = new RedisVerificationCodeStore(store);
        String email = email();
        String code = codes.issue(CodePurpose.SIGNUP, email).block();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> codes.verify(CodePurpose.TWO_FACTOR, email, code).block())
                .isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void fifthWrongAttemptBurnsTheCode() {
        var codes = new RedisVerificationCodeStore(store);
        String email = email();
        String code = codes.issue(CodePurpose.PASSWORD_RESET, email).block();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> codes.verify(CodePurpose.PASSWORD_RESET, email, wrong).block())
                    .isInstanceOf(DomainException.Unauthorized.class);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> codes.verify(CodePurpose.PASSWORD_RESET, email, wrong).block())
                .isInstanceOf(DomainException.TooManyRequests.class);
        // o código certo também deixou de valer
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> codes.verify(CodePurpose.PASSWORD_RESET, email, code).block())
                .isInstanceOf(DomainException.Unauthorized.class);
    }

    @Test
    void resendIsBlockedForOneMinute() {
        var codes = new RedisVerificationCodeStore(store);
        String email = email();
        codes.issue(CodePurpose.TWO_FACTOR, email).block();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> codes.issue(CodePurpose.TWO_FACTOR, email).block())
                .isInstanceOf(DomainException.TooManyRequests.class);
        // outra finalidade não é afetada
        assertThat(codes.issue(CodePurpose.SIGNUP, email).block()).matches("\\d{6}");
    }

    @Test
    void refreshTokenIsOnePerUserAndRevocable() {
        var refresh = new RedisRefreshTokenStore(store);
        UUID user = UUID.randomUUID();
        assertThat(refresh.isCurrent(user, "a").block()).isFalse();
        refresh.save(user, "a").block();
        assertThat(refresh.isCurrent(user, "a").block()).isTrue();
        refresh.save(user, "b").block();
        assertThat(refresh.isCurrent(user, "a").block()).isFalse();
        assertThat(refresh.isCurrent(user, "b").block()).isTrue();
        assertThat(store.ttl("refresh:" + user).block()).isPositive();
        refresh.revoke(user).block();
        assertThat(refresh.isCurrent(user, "b").block()).isFalse();
    }

    @Test
    void rateLimiterBlocksAboveTheLimitAndKeysAreIndependent() {
        var limiter = new RedisRateLimiter(store);
        String key = "ip:" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            limiter.check(key, 3).block();
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> limiter.check(key, 3).block())
                .isInstanceOf(DomainException.TooManyRequests.class);
        limiter.check("ip:" + UUID.randomUUID(), 3).block();
        assertThat(store.ttl("rate_limit:" + key).block()).isPositive();
    }
}
