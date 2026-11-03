package br.rhhub.infrastructure.mail;

import br.rhhub.application.port.out.EmailTemplate;
import br.rhhub.application.port.out.EmailTemplates;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Lê templates/&lt;arquivo&gt;.html do classpath e troca cada {@code {}} pelos valores, na ordem. */
@Component
public class ClasspathEmailTemplates implements EmailTemplates {

    private static final String PLACEHOLDER = "{}";

    private final Map<EmailTemplate, String> cache = new ConcurrentHashMap<>();

    @Override
    public String render(EmailTemplate template, String... values) {
        String html = cache.computeIfAbsent(template, ClasspathEmailTemplates::load);
        var out = new StringBuilder(html.length() + 64);
        int from = 0;
        for (String value : values) {
            int at = html.indexOf(PLACEHOLDER, from);
            if (at < 0) {
                throw new IllegalArgumentException("Valores demais para o template " + template.file());
            }
            out.append(html, from, at).append(HtmlUtils.htmlEscape(value == null ? "" : value));
            from = at + PLACEHOLDER.length();
        }
        if (html.indexOf(PLACEHOLDER, from) >= 0) {
            throw new IllegalArgumentException("Faltam valores para o template " + template.file());
        }
        return out.append(html, from, html.length()).toString();
    }

    private static String load(EmailTemplate template) {
        try (var in = new ClassPathResource("templates/" + template.file() + ".html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Template não encontrado: " + template.file(), e);
        }
    }
}
