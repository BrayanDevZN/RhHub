package br.rhhub.application.port.out;

/** Enfileira a candidatura no topic resume.analysis. A mensagem leva só o id. */
public interface ResumeAnalysisPublisher {

    void publish(Long resumeId);
}
