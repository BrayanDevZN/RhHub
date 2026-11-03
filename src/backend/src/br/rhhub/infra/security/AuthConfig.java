package br.rhhub.infra.security;

import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.auth.JwtService;
import br.rhhub.infra.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    /**
     * Com JWT_SECRET usa o valor. Sem ele, gera 48 bytes aleatórios na subida: ninguém (nem quem opera o serviço)
     * conhece o segredo, mas cada reinício invalida as sessões, e duas réplicas da API não aceitam os tokens uma da outra.
     */
    @Bean
    JwtService jwtService(AuthProperties props, ObjectMapper mapper) {
        return new JwtService(resolveSecret(props.jwtSecret()), mapper);
    }

    @Bean
    AccessPolicy accessPolicy(AuthProperties props) {
        return new AccessPolicy(props.adminEmail());
    }

    static String resolveSecret(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        byte[] random = new byte[48];
        new SecureRandom().nextBytes(random);
        log.warn("JWT_SECRET não definido: usando um segredo aleatório desta execução (sessões acabam ao reiniciar)");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}
