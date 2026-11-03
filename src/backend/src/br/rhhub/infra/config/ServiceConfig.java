package br.rhhub.infra.config;

import br.rhhub.application.port.in.DeliverEmail;
import br.rhhub.application.port.out.EmailSender;
import br.rhhub.service.DeliverEmailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Monta os serviços, que não conhecem o Spring, com as implementações da infra. */
@Configuration
public class ServiceConfig {

    @Bean
    DeliverEmail deliverEmail(EmailSender sender) {
        return new DeliverEmailService(sender);
    }
}
