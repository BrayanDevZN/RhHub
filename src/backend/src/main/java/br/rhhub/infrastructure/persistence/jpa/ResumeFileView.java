package br.rhhub.infrastructure.persistence.jpa;

public interface ResumeFileView {

    byte[] getFile();

    String getFileName();

    String getContentType();
}
