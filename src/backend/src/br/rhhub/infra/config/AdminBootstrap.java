package br.rhhub.infra.config;

import br.rhhub.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Na subida, cria o admin raiz a partir de ADMIN_EMAIL e ADMIN_PASSWORD. Dado inválido só gera log: o app sobe. */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    ApplicationRunner rootAdminRunner(AuthProperties props, AuthService auth) {
        return args -> {
            if (props.adminEmail() == null || props.adminEmail().isBlank()) {
                log.info("ADMIN_EMAIL não definido: nenhum admin raiz será criado");
                return;
            }
            auth.ensureRootAdmin(props.adminEmail(), props.adminPassword() == null ? "" : props.adminPassword())
                    .subscribe(
                            admin -> log.info("Admin raiz pronto: {}", admin.email()),
                            error -> log.error("Não foi possível preparar o admin raiz: {}", error.getMessage()));
        };
    }
}
