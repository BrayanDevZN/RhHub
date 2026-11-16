package br.rhhub.domain.model;

/** O arquivo de um currículo: bytes, nome original e content-type (usado no download). */
public record ResumeFile(byte[] content, String fileName, String contentType) {
}
