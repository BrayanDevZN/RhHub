package br.rhhub.application.port.out;

public interface EmailTemplates {

    /** Preenche, na ordem, cada {} do template com os valores (escapados para HTML). */
    String render(EmailTemplate template, String... values);
}
