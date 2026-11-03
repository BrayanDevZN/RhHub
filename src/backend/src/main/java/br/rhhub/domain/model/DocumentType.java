package br.rhhub.domain.model;

import java.util.Optional;

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

    public static Optional<DocumentType> fromContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        String base = contentType.split(";")[0].trim();
        for (DocumentType type : values()) {
            if (type.contentType.equalsIgnoreCase(base)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
