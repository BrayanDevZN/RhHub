package br.rhhub.testing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Teste "live": chama um serviço externo de verdade (Groq, n8n/Gmail) e gasta cota ou envia e-mail real. Fica FORA de
 * todas as execuções, inclusive do CI, e só roda quando pedido: {@code mvn test -Pcategory-live} (com as chaves no
 * ambiente).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("live")
public @interface LiveTest {
}
