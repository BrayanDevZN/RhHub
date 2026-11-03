package br.rhhub.infra.database.jpa;

public interface ResumeFileView {

    byte[] getFile();

    String getFileName();

    String getContentType();
}
