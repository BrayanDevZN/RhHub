package br.rhhub.infrastructure.config;

import br.rhhub.application.port.in.DeliverEmail;
import br.rhhub.application.port.out.MailSender;
import br.rhhub.application.usecase.DeliverEmailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Monta os casos de uso, que não conhecem o Spring, com as implementações da infra. */
@Configuration
public class UseCaseConfig {

    @Bean
    DeliverEmail deliverEmail(MailSender mailSender) {
        return new DeliverEmailService(mailSender);
    }
}
