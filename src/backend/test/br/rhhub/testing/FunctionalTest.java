package br.rhhub.testing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Teste funcional: exercita o sistema por fora, pela API HTTP ou pelo fluxo completo (candidatura, fila, IA e e-mail), com o ambiente ENVIRONMENT=test.
 * Rodar só esta categoria: {@code mvn test -Pcategory-functional}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("functional")
public @interface FunctionalTest {
}
