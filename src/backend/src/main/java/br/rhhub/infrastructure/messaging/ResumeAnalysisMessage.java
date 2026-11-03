package br.rhhub.infrastructure.messaging;

/** Mensagem do topic resume.analysis: só o id, o consumer busca o resto no banco. */
public record ResumeAnalysisMessage(Long resumeId) {
}
