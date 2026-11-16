package br.rhhub.application.port.in;

import reactor.core.publisher.Mono;

/** Analisa uma candidatura (lê o currículo, chama a IA, grava o resultado e pede o e-mail). */
public interface AnalyzeResume {

    Mono<Void> execute(Long resumeId);
}
