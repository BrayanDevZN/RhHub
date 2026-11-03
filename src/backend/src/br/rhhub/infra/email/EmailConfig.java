package br.rhhub.infra.email;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailSender;
import br.rhhub.infra.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Escolhe o envio: ENVIRONMENT=test não envia nada (só registra no log); com N8N_JWT_SECRET usa o n8n;
 * sem o segredo a aplicação sobe, mas cada envio falha com EmailException.
 */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    EmailSender emailSender(AppProperties props, Clock clock, ObjectMapper mapper) {
        var email = props.email();
        if (props.isTest()) {
            return new EmailSender() {
                @Override
                public Mono<Void> send(EmailMessage message) {
                    log.info("E-mail de teste (não enviado): '{}' para {}", message.subject(), message.to());
                    return Mono.empty();
                }

                @Override
                public String name() {
                    return "fixed";
                }
            };
        }
        if (email.jwtSecret() != null && !email.jwtSecret().isBlank()) {
            return new N8nEmailSender(email.webhookUrl(), email.jwtSecret().strip(), email.timeout(), clock, mapper);
        }
        return new EmailSender() {
            @Override
            public Mono<Void> send(EmailMessage message) {
                return Mono.error(new EmailException("N8N_JWT_SECRET não configurado: e-mail não enviado"));
            }

            @Override
            public String name() {
                return "none";
            }
        };
    }
}
