package br.rhhub.infra.redis;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.infra.config.AppProperties;
import org.junit.jupiter.api.Test;

@UnitTest
class RedisConfigTest {

    @Test
    void noPasswordMeansHostAndPortOnly() {
        for (String password : new String[] {null, "", "   ", "none", "NONE", " None ", "null"}) {
            var config = RedisConfig.standalone(new AppProperties.Redis("redis.interno", 6380, password));
            assertThat(config.getHostName()).isEqualTo("redis.interno");
            assertThat(config.getPort()).isEqualTo(6380);
            assertThat(config.getPassword().isPresent()).as("[%s]", password).isFalse();
        }
    }

    @Test
    void passwordIsUsedWhenPresent() {
        var config = RedisConfig.standalone(new AppProperties.Redis("h", 6379, " segredo "));
        assertThat(config.getPassword().isPresent()).isTrue();
        assertThat(new String(config.getPassword().get())).isEqualTo("segredo");
    }

    @Test
    void missingEnvsFallBackToLocalDefault() {
        var redis = new AppProperties.Redis(null, 6379, null);
        assertThat(redis.host()).isEqualTo("localhost");
        assertThat(redis.hasPassword()).isFalse();
        assertThat(new AppProperties.Redis("  ", 6379, null).host()).isEqualTo("localhost");
    }

    @Test
    void invalidPortFailsFast() {
        assertThatThrownBy(() -> new AppProperties.Redis("h", 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppProperties.Redis("h", 70000, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
