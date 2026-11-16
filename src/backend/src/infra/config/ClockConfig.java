package br.rhhub.infra.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fornece o relógio (UTC) como bean; nos testes ele é trocado por um relógio fixo. */
@Configuration
public class ClockConfig {

    /**
     * Fornece o relógio do sistema em UTC. Todo código que precisa da hora recebe este relógio, e os testes o trocam por um fixo.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
