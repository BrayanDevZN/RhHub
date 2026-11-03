package br.rhhub.application.port.out;

import br.rhhub.domain.auth.CodePurpose;

/** Monta o e-mail que leva um código de verificação. */
public interface EmailComposer {

    EmailMessage verificationCode(CodePurpose purpose, String to, String name, String code);

    /** Avisa o candidato de que a candidatura mudou de etapa. position começa em 1; total é o número de etapas da vaga. */
    EmailMessage stageChanged(String to, String name, String vacancy, String stage, int position, int total);
}
