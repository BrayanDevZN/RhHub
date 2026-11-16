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

/** Libera só as origens de FRONTEND_URL, com cookies. Sem a variável, nenhuma origem externa é aceita. */
@Configuration
public class CorsConfig {

    /** Roda antes do filtro de autenticação, para a resposta ao preflight (OPTIONS) nunca exigir login. */
    static class OrderedCorsFilter extends CorsWebFilter implements Ordered {
        /**
         * Cria o filtro de CORS com a configuração dada.
         */
        OrderedCorsFilter(UrlBasedCorsConfigurationSource source) {
            super(source);
        }

        /**
         * Posição do filtro na fila: logo no começo (depois dos cabeçalhos de segurança), para as requisições de verificação do navegador (OPTIONS) serem respondidas antes do login.
         */
        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 10;
        }
    }

    /**
     * Monta o CORS: define quais endereços de front podem chamar a API.
     *
     * Só aceita as origens da variável FRONTEND_URL (vazia = nenhuma), os métodos GET, POST, PATCH, PUT, DELETE e OPTIONS e os cabeçalhos Content-Type e Accept. Permite enviar cookies (credenciais) e o navegador guarda a resposta de verificação por 1 hora.
     */
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
