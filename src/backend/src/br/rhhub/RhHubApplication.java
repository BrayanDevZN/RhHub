package br.rhhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** O banco é configurado em infra/database (DATABASE_URL); por isso as auto-configurações de JDBC/R2DBC/Flyway ficam de fora. */
@SpringBootApplication(excludeName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration",
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
@ConfigurationPropertiesScan
public class RhHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(RhHubApplication.class, args);
    }
}
