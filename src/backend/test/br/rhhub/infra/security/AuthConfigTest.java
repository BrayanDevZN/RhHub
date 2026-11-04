package br.rhhub.infra.security;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.domain.auth.JwtService;
import br.rhhub.domain.auth.TokenType;
import br.rhhub.infra.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@UnitTest
class AuthConfigTest {

    @Test
    void withoutJwtSecretARandomLongOneIsGeneratedEachTime() {
        for (String empty : new String[] {null, "", "  "}) {
            String a = AuthConfig.resolveSecret(empty);
            String b = AuthConfig.resolveSecret(empty);
            assertThat(a).hasSizeGreaterThanOrEqualTo(JwtService.MIN_SECRET_LENGTH).isNotEqualTo(b);
        }
    }

    @Test
    void configuredSecretIsKept() {
        assertThat(AuthConfig.resolveSecret("meu-segredo-fixo-com-mais-de-32-caracteres")).isEqualTo("meu-segredo-fixo-com-mais-de-32-caracteres");
    }

    @Test
    void beanWorksWithoutAnySecret() {
        var props = new AuthProperties(null, "adm@gmail.com", null, 60, 1000, null);
        var jwt = new AuthConfig().jwtService(props, new ObjectMapper());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        assertThat(jwt.verify(jwt.issue(id, TokenType.ACCESS, now), TokenType.ACCESS, now).subject()).isEqualTo(id);
        assertThat(props.google().enabled()).isFalse();
    }
}
