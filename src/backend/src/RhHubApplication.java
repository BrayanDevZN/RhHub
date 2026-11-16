package br.rhhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** O banco é configurado em infra/database (DATABASE_URL); por isso as auto-configurações de JDBC/R2DBC ficam de fora. */
@SpringBootApplication(excludeName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration"
})
/** Ponto de entrada da API: sobe o Spring Boot (WebFlux). */
@ConfigurationPropertiesScan
public class RhHubApplication {

    /**
     * Inicia a aplicação. O mesmo jar roda a API e o serviço consumer: o que muda é a variável KAFKA_CONSUMERS_ENABLED (desligada na API, que só publica nas filas, e ligada no consumer, que lê as filas).
     */
    public static void main(String[] args) {
        SpringApplication.run(RhHubApplication.class, args);
    }
}
