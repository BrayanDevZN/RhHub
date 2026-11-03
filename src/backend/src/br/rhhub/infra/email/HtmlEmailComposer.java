package br.rhhub.infra.email;

import br.rhhub.application.port.out.EmailComposer;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.domain.auth.CodePurpose;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Templates HTML de resources/templates (os do AgentRH). Os dois campos, {{name}} e {{code}}, entram escapados. */
@Component
public class HtmlEmailComposer implements EmailComposer {

    private final Map<CodePurpose, String> templates = new EnumMap<>(CodePurpose.class);

    public HtmlEmailComposer() {
        templates.put(CodePurpose.SIGNUP, load("create_account"));
        templates.put(CodePurpose.TWO_FACTOR, load("two_factor_authentication"));
        templates.put(CodePurpose.PASSWORD_RESET, load("update_password"));
    }

    @Override
    public EmailMessage verificationCode(CodePurpose purpose, String to, String name, String code) {
        String html = templates.get(purpose)
                .replace("{{name}}", escape(name))
                .replace("{{code}}", escape(code));
        return EmailMessage.html(to, subject(purpose), html);
    }

    static String subject(CodePurpose purpose) {
        return switch (purpose) {
            case SIGNUP -> "Seu código para criar a conta";
            case TWO_FACTOR -> "Seu código de verificação em duas etapas";
            case PASSWORD_RESET -> "Seu código para alterar a senha";
        };
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String load(String name) {
        try (var in = new ClassPathResource("templates/" + name + ".html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Template de e-mail não encontrado: templates/" + name + ".html", e);
        }
    }
}
