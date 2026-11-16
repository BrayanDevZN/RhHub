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

/** Templates HTML de infra/resources/templates (os do AgentRH). Os dois campos, {{name}} e {{code}}, entram escapados. */
@Component
public class HtmlEmailComposer implements EmailComposer {

    private final Map<CodePurpose, String> templates = new EnumMap<>(CodePurpose.class);

    /**
     * Carrega na memória, uma vez, os três modelos de e-mail de código (cadastro, 2FA e troca de senha) da pasta templates.
     *
     * @throws UncheckedIOException se algum modelo não for encontrado.
     */
    public HtmlEmailComposer() {
        templates.put(CodePurpose.SIGNUP, load("create_account"));
        templates.put(CodePurpose.TWO_FACTOR, load("two_factor_authentication"));
        templates.put(CodePurpose.PASSWORD_RESET, load("update_password"));
    }

    /**
     * Monta o e-mail com o código de verificação. Pega o modelo da finalidade e troca {{name}} e {{code}} pelos valores, já com os caracteres especiais escapados.
     *
     * @return o e-mail em HTML, pronto para enviar.
     */
    @Override
    public EmailMessage verificationCode(CodePurpose purpose, String to, String name, String code) {
        String html = templates.get(purpose)
                .replace("{{name}}", escape(name))
                .replace("{{code}}", escape(code));
        return EmailMessage.html(to, subject(purpose), html);
    }

    /**
     * Monta o e-mail avisando que a candidatura avançou de etapa: nome da vaga, etapa atual e a posição ("etapa 2 de 3"). Todos os textos são escapados.
     *
     * @return o e-mail em HTML.
     */
    @Override
    public EmailMessage stageChanged(String to, String name, String vacancy, String stage, int position, int total) {
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px;color:#1a1a2e">
                  <h2 style="margin:0 0 12px">Sua candidatura avançou</h2>
                  <p>Olá, %s!</p>
                  <p>Sua candidatura para a vaga <strong>%s</strong> está agora na etapa
                     <strong>%s</strong> (etapa %d de %d).</p>
                  <p style="color:#555">Você pode acompanhar o andamento a qualquer momento em
                     <em>Minhas candidaturas</em> no RhHub.</p>
                </div>""".formatted(escape(name), escape(vacancy), escape(stage), position, total);
        return EmailMessage.html(to, "Sua candidatura para " + vacancy + ": etapa " + stage, html);
    }

    /**
     * Assunto do e-mail de código conforme a finalidade.
     */
    static String subject(CodePurpose purpose) {
        return switch (purpose) {
            case SIGNUP -> "Seu código para criar a conta";
            case TWO_FACTOR -> "Seu código de verificação em duas etapas";
            case PASSWORD_RESET -> "Seu código para alterar a senha";
        };
    }

    /**
     * Troca os caracteres especiais do HTML (& < > " ') por entidades, para um nome ou texto digitado pelo usuário nunca virar código dentro do e-mail. Nulo vira vazio.
     */
    static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * Lê o modelo HTML templates/<nome>.html de dentro do jar.
     *
     * @throws UncheckedIOException se não existir.
     */
    private static String load(String name) {
        try (var in = new ClassPathResource("templates/" + name + ".html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Template de e-mail não encontrado: templates/" + name + ".html", e);
        }
    }
}
