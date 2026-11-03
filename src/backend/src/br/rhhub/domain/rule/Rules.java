package br.rhhub.domain.rule;

import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Gender;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras de validação dos campos (seção 4.10 da DOCUMENTACAO.md). Cada método devolve o valor já normalizado ou
 * lança {@link DomainException.Invalid} (400) com uma mensagem que o front pode mostrar.
 */
public final class Rules {

    public static final String EMAIL_SUFFIX = "@gmail.com";
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 72; // limite do BCrypt
    public static final int AGE_MIN = 18;
    public static final int AGE_MAX = 120;
    public static final int NAME_MAX = 120;
    public static final int VACANCY_NAME_MAX = 120;
    public static final int VACANCY_DESCRIPTION_MAX = 5000;

    private static final Pattern EMAIL_LOCAL = Pattern.compile("[a-z0-9](?:[a-z0-9._+-]{0,62}[a-z0-9])?");
    private static final Pattern CPF = Pattern.compile("\\d{11}");
    private static final Pattern CODE = Pattern.compile("\\d{6}");

    private Rules() {
    }

    /** Normaliza para minúsculas e exige terminar exatamente em @gmail.com (não aceita x@gmail.com.outro.com). */
    public static String email(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException.Invalid("Informe o e-mail.");
        }
        String value = raw.strip().toLowerCase(Locale.ROOT);
        if (!value.endsWith(EMAIL_SUFFIX)) {
            throw new DomainException.Invalid("Use um e-mail @gmail.com.");
        }
        String local = value.substring(0, value.length() - EMAIL_SUFFIX.length());
        if (!EMAIL_LOCAL.matcher(local).matches() || local.contains("..")) {
            throw new DomainException.Invalid("E-mail inválido.");
        }
        return value;
    }

    /** Mínimo de 8 caracteres, com maiúscula, minúscula e número. */
    public static String password(String raw) {
        if (raw == null || raw.length() < PASSWORD_MIN) {
            throw new DomainException.Invalid("A senha precisa ter no mínimo " + PASSWORD_MIN + " caracteres.");
        }
        if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > PASSWORD_MAX) {
            throw new DomainException.Invalid("A senha pode ter no máximo " + PASSWORD_MAX + " bytes.");
        }
        boolean upper = raw.chars().anyMatch(Character::isUpperCase);
        boolean lower = raw.chars().anyMatch(Character::isLowerCase);
        boolean digit = raw.chars().anyMatch(Character::isDigit);
        if (!(upper && lower && digit)) {
            throw new DomainException.Invalid("A senha precisa ter letra maiúscula, minúscula e número.");
        }
        return raw;
    }

    public static String name(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException.Invalid("Informe o nome.");
        }
        String value = raw.strip().replaceAll("\\s+", " ");
        if (value.length() > NAME_MAX) {
            throw new DomainException.Invalid("O nome pode ter no máximo " + NAME_MAX + " caracteres.");
        }
        return value;
    }

    /** Exatamente 11 dígitos. Pontos e traço da máscara são aceitos na entrada e removidos. */
    public static String cpf(String raw) {
        String value = raw == null ? "" : raw.strip().replaceAll("[.\\-\\s]", "");
        if (!CPF.matcher(value).matches()) {
            throw new DomainException.Invalid("O CPF precisa ter 11 dígitos.");
        }
        return value;
    }

    public static int age(Integer raw) {
        if (raw == null || raw < AGE_MIN) {
            throw new DomainException.Invalid("É preciso ter " + AGE_MIN + " anos ou mais.");
        }
        if (raw > AGE_MAX) {
            throw new DomainException.Invalid("Idade inválida.");
        }
        return raw;
    }

    public static Gender gender(String raw) {
        try {
            return Gender.from(raw);
        } catch (RuntimeException e) {
            throw new DomainException.Invalid("Gênero inválido: use male, female ou other.");
        }
    }

    /** Código de verificação: 6 dígitos. */
    public static String code(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (!CODE.matcher(value).matches()) {
            throw new DomainException.Invalid("O código precisa ter 6 dígitos.");
        }
        return value;
    }

    public static String vacancyName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException.Invalid("Informe o nome da vaga.");
        }
        String value = raw.strip().replaceAll("\\s+", " ");
        if (value.length() > VACANCY_NAME_MAX) {
            throw new DomainException.Invalid("O nome da vaga pode ter no máximo " + VACANCY_NAME_MAX + " caracteres.");
        }
        return value;
    }

    /** Texto opcional (requisitos, responsabilidades): vazio vira nulo; só se limita o tamanho. */
    public static String optionalText(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (value.length() > VACANCY_DESCRIPTION_MAX) {
            throw new DomainException.Invalid(label + " pode ter no máximo " + VACANCY_DESCRIPTION_MAX + " caracteres.");
        }
        return value;
    }

    public static String vacancyDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException.Invalid("Informe a descrição da vaga.");
        }
        String value = raw.strip();
        if (value.length() > VACANCY_DESCRIPTION_MAX) {
            throw new DomainException.Invalid(
                    "A descrição pode ter no máximo " + VACANCY_DESCRIPTION_MAX + " caracteres.");
        }
        return value;
    }
}
