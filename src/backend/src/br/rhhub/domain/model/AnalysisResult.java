package br.rhhub.domain.model;

import br.rhhub.domain.exception.DomainException;
import java.util.regex.Pattern;

/**
 * Resposta da IA já interpretada. O prompt manda responder numa linha só, no formato
 * {@code DECISÃO | ASSUNTO | HTML}, com a decisão exatamente {@code aproved} ou {@code recuse} (a grafia é proposital).
 */
public record AnalysisResult(ResumeStatus decision, String subject, String html) {

    private static final String SEPARATOR = " | ";
    private static final int MAX_REASON = 4000;
    private static final Pattern BLOCKS = Pattern.compile("(?is)<(head|style|script)\\b.*?</\\1>");
    private static final Pattern BREAKS = Pattern.compile("(?i)<br\\s*/?>|</(p|div|tr|li|h[1-6]|table|section)>");
    private static final Pattern CELLS = Pattern.compile("(?i)</t[dh]>");
    private static final Pattern TAGS = Pattern.compile("(?s)<[^>]*>");

    /**
     * Lê a resposta da IA. O assunto nunca tem "|", então o segundo separador é o primeiro que vem depois do
     * primeiro (o HTML pode ter). Qualquer desvio do formato é {@code Unprocessable}: a candidatura continua
     * pendente e o job pode ser repetido.
     */
    public static AnalysisResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException.Unprocessable("A IA devolveu uma resposta vazia");
        }
        String text = withoutCodeFence(raw.strip());
        int first = text.indexOf(SEPARATOR);
        int second = first < 0 ? -1 : text.indexOf(SEPARATOR, first + SEPARATOR.length());
        if (first < 0 || second < 0) {
            throw new DomainException.Unprocessable("A resposta da IA não segue o formato DECISÃO | ASSUNTO | HTML");
        }
        String token = text.substring(0, first).strip();
        ResumeStatus decision;
        if ("aproved".equalsIgnoreCase(token)) {
            decision = ResumeStatus.APROVED;
        } else if ("recuse".equalsIgnoreCase(token)) {
            decision = ResumeStatus.RECUSE;
        } else {
            throw new DomainException.Unprocessable("Decisão inválida na resposta da IA: '" + abbreviate(token, 40) + "'");
        }
        String subject = text.substring(first + SEPARATOR.length(), second).strip().replaceAll("\\s+", " ");
        String html = text.substring(second + SEPARATOR.length()).strip();
        if (subject.isEmpty()) {
            throw new DomainException.Unprocessable("A resposta da IA veio sem assunto");
        }
        if (!html.toLowerCase().contains("<html")) {
            throw new DomainException.Unprocessable("A resposta da IA veio sem o corpo HTML");
        }
        return new AnalysisResult(decision, subject, html);
    }

    /** Texto que fica em resumes.reason: o assunto e o corpo sem as tags, legível no front. */
    public String reason() {
        String body = BLOCKS.matcher(html).replaceAll(" ");
        body = BREAKS.matcher(body).replaceAll("\n");
        body = CELLS.matcher(body).replaceAll(" ");
        body = TAGS.matcher(body).replaceAll("");
        body = body.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
        body = body.replaceAll("[ \\t\\u00A0]+", " ").replaceAll(" ?\\n ?", "\n").replaceAll("\\n{3,}", "\n\n").strip();
        return abbreviate(subject + "\n\n" + body, MAX_REASON);
    }

    private static String withoutCodeFence(String text) {
        String result = text;
        if (result.startsWith("```")) {
            int newline = result.indexOf('\n');
            result = newline < 0 ? "" : result.substring(newline + 1);
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }
        return result.strip();
    }

    private static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
