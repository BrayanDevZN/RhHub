package br.rhhub.application.web;

import br.rhhub.infra.config.WebProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/** Libera só as origens de FRONTEND_ORIGIN, com cookies. Sem a variável, nenhuma origem externa é aceita. */
@Configuration
public class CorsConfig {

    /** Roda antes do filtro de autenticação, para a resposta ao preflight (OPTIONS) nunca exigir login. */
    static class OrderedCorsFilter extends CorsWebFilter implements Ordered {
        OrderedCorsFilter(UrlBasedCorsConfigurationSource source) {
            super(source);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 10;
        }
    }

    @Bean
    CorsWebFilter corsWebFilter(WebProperties web) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(web.origins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new OrderedCorsFilter(source);
    }
}
