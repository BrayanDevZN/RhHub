package br.rhhub.application.port.out;

import br.rhhub.domain.auth.CodePurpose;

/** Monta o e-mail que leva um código de verificação. */
public interface EmailComposer {

    EmailMessage verificationCode(CodePurpose purpose, String to, String name, String code);
}
