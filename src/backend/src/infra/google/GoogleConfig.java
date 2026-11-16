package br.rhhub.infra.google;

import br.rhhub.application.port.out.GoogleIdentityProvider;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.infra.config.AuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/** Desligado por padrão (GOOGLE_OAUTH_ENABLED=false). Ligado sem as três variáveis, a subida falha com a lista do que falta. */
@Configuration
public class GoogleConfig {

    /**
     * Escolhe o login com Google. Desligado (padrão), devolve uma versão que responde 404. Ligado, exige GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET e GOOGLE_REDIRECT_URI e cria o cliente real.
     *
     * @throws IllegalStateException se estiver ligado e faltar alguma das três variáveis (a aplicação não sobe, para o erro aparecer logo).
     */
    @Bean
    GoogleIdentityProvider googleIdentityProvider(AuthProperties props) {
        var google = props.google();
        if (!google.enabled()) {
            return disabled();
        }
        var missing = new java.util.ArrayList<String>();
        if (blank(google.clientId())) {
            missing.add("GOOGLE_CLIENT_ID");
        }
        if (blank(google.clientSecret())) {
            missing.add("GOOGLE_CLIENT_SECRET");
        }
        if (blank(google.redirectUri())) {
            missing.add("GOOGLE_REDIRECT_URI");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("GOOGLE_OAUTH_ENABLED=true exige: " + String.join(", ", missing));
        }
        return new GoogleOAuthClient(google.clientId().strip(), google.clientSecret().strip(), google.redirectUri().strip());
    }

    /**
     * Versão desligada: {@code enabled()} é falso e qualquer outra chamada falha com 404 "Login com Google desativado".
     */
    static GoogleIdentityProvider disabled() {
        return new GoogleIdentityProvider() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public String authorizationUrl(String state) {
                throw new DomainException.NotFound("Login com Google desativado.");
            }

            @Override
            public Mono<Profile> fetchProfile(String code) {
                return Mono.error(new DomainException.NotFound("Login com Google desativado."));
            }
        };
    }

    /**
     * Diz se o texto é nulo ou só tem espaços.
     */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
