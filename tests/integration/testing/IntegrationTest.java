package br.rhhub.testing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Teste de integração: fala com uma peça real de infraestrutura (SQLite, PostgreSQL, Redis, Kafka embarcado ou um servidor HTTP local), uma de cada vez.
 * Rodar só esta categoria: {@code mvn test -Pcategory-integration}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("integration")
public @interface IntegrationTest {
}
