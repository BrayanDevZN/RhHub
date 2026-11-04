package br.rhhub.testing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Teste unitário: roda só na memória, sem rede, sem banco e sem arquivos. Usa repositórios falsos (Fakes) ou mocks.
 * Rodar só esta categoria: {@code mvn test -Pcategory-unit}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("unit")
public @interface UnitTest {
}
