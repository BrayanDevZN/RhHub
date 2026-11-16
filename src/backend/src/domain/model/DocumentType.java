package br.rhhub.domain.model;

/** Tipos de currículo aceitos (PDF e DOCX) e o content-type de cada um. */
public enum DocumentType {
    PDF("application/pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final String contentType;

    /**
     * Associa o tipo de documento ao seu content-type.
     */
    DocumentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Content-type HTTP deste tipo (usado no download do currículo).
     */
    public String contentType() {
        return contentType;
    }
}
