package br.rhhub.domain;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Gender;
import br.rhhub.domain.rule.Rules;
import org.junit.jupiter.api.Test;

@UnitTest
class RulesTest {

    @Test
    void emailMustEndExactlyInGmailAndIsLowercased() {
        assertThat(Rules.email("  Ana.Silva@GMAIL.com ")).isEqualTo("ana.silva@gmail.com");
        for (String bad : new String[] {"x@gmail.com.outro.com", "x@outlook.com", "", null, "@gmail.com", "a..b@gmail.com",
                ".a@gmail.com", "a b@gmail.com", "gmail.com"}) {
            assertThatThrownBy(() -> Rules.email(bad)).as(String.valueOf(bad)).isInstanceOf(DomainException.Invalid.class);
        }
    }

    @Test
    void passwordNeedsLengthUpperLowerAndDigit() {
        assertThat(Rules.password("Senha123")).isEqualTo("Senha123");
        for (String bad : new String[] {"Sen1", "senha1234", "SENHA1234", "SenhaSenha", null, "A1a".repeat(30)}) {
            assertThatThrownBy(() -> Rules.password(bad)).as(String.valueOf(bad)).isInstanceOf(DomainException.Invalid.class);
        }
    }

    @Test
    void cpfIsElevenDigitsAndMaskIsRemoved() {
        assertThat(Rules.cpf("123.456.789-01")).isEqualTo("12345678901");
        assertThat(Rules.cpf("12345678901")).isEqualTo("12345678901");
        for (String bad : new String[] {"1234567890", "123456789012", "abcdefghijk", "", null}) {
            assertThatThrownBy(() -> Rules.cpf(bad)).as(String.valueOf(bad)).isInstanceOf(DomainException.Invalid.class);
        }
    }

    @Test
    void ageGenderAndCode() {
        assertThat(Rules.age(18)).isEqualTo(18);
        assertThatThrownBy(() -> Rules.age(17)).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> Rules.age(null)).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> Rules.age(200)).isInstanceOf(DomainException.Invalid.class);

        assertThat(Rules.gender("Female")).isEqualTo(Gender.FEMALE);
        assertThatThrownBy(() -> Rules.gender("x")).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> Rules.gender(null)).isInstanceOf(DomainException.Invalid.class);

        assertThat(Rules.code(" 123456 ")).isEqualTo("123456");
        for (String bad : new String[] {"12345", "1234567", "12345a", null}) {
            assertThatThrownBy(() -> Rules.code(bad)).isInstanceOf(DomainException.Invalid.class);
        }
    }

    @Test
    void vacancyAndName() {
        assertThat(Rules.vacancyName("  Dev   Java ")).isEqualTo("Dev Java");
        assertThatThrownBy(() -> Rules.vacancyName(" ")).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> Rules.vacancyName("x".repeat(121))).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> Rules.vacancyDescription(null)).isInstanceOf(DomainException.Invalid.class);
        assertThat(Rules.name(" Ana  Souza ")).isEqualTo("Ana Souza");
        assertThatThrownBy(() -> Rules.name("")).isInstanceOf(DomainException.Invalid.class);
    }
}
