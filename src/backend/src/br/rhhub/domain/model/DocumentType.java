package br.rhhub.domain.model;

/** Tipos de currículo aceitos (PDF e DOCX) e o content-type de cada um. */
public enum DocumentType {
    PDF("application/pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final String contentType;

    DocumentType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }
}
