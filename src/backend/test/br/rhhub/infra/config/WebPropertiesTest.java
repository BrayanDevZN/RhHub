package br.rhhub.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebPropertiesTest {

    private static WebProperties of(String origin) {
        return new WebProperties(origin, null, "Strict");
    }

    @Test
    void addsHttpsWhenTheSchemeIsMissingSoRedirectsAreNeverRelative() {
        assertThat(of("rhhub-production.up.railway.app").origins()).containsExactly("https://rhhub-production.up.railway.app");
        assertThat(of("rhhub-production.up.railway.app").homeUrl()).isEqualTo("https://rhhub-production.up.railway.app/");
    }

    @Test
    void keepsAnExplicitSchemeAndDropsTrailingSlashesAndQuotes() {
        assertThat(of("https://app.exemplo.com/").origins()).containsExactly("https://app.exemplo.com");
        assertThat(of(" \"https://app.exemplo.com//\" ").origins()).containsExactly("https://app.exemplo.com");
        assertThat(of("http://localhost:9000").origins()).containsExactly("http://localhost:9000");
    }

    @Test
    void localHostsGetHttpAndSeveralOriginsAreSplit() {
        assertThat(of("localhost:9000").origins()).containsExactly("http://localhost:9000");
        assertThat(of("127.0.0.1:19100, front.exemplo.com").origins())
                .containsExactly("http://127.0.0.1:19100", "https://front.exemplo.com");
    }

    @Test
    void emptyMeansNoOriginAndHomeIsTheRoot() {
        assertThat(of(null).origins()).isEmpty();
        assertThat(of("  ").origins()).isEmpty();
        assertThat(of(" , ").origins()).isEmpty();
        assertThat(of(null).homeUrl()).isEqualTo("/");
    }
}
