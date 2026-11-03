package br.rhhub.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/** Provisório: nega tudo até a camada de apresentação (rotas e filtro JWT) existir. */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain denyAll(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .build();
    }
}
