package br.rhhub.infra.messaging;

/** Mensagem do tópico de análise: só o id. O consumer busca o PDF, a vaga e o candidato no banco. */
public record ResumeAnalysisMessage(Long resumeId) {
}
