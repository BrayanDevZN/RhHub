package br.rhhub.application.port.out;

/** {@code content} é o HTML pronto quando type é HTML, ou texto puro quando é TEXT. */
public record EmailMessage(String to, String subject, String content, Type type) {

    public enum Type {
        HTML,
        TEXT
    }

    /**
     * Cria um e-mail com corpo em HTML.
     */
    public static EmailMessage html(String to, String subject, String html) {
        return new EmailMessage(to, subject, html, Type.HTML);
    }

    /**
     * Cria um e-mail com corpo em texto simples.
     */
    public static EmailMessage text(String to, String subject, String text) {
        return new EmailMessage(to, subject, text, Type.TEXT);
    }
}
