package br.rhhub.domain.model;

public record ResumeFile(byte[] content, String fileName, String contentType) {
}
