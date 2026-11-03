package br.rhhub.application.port.out;

import java.util.Optional;

/** Códigos temporários de verificação, com contagem de tentativas. */
public interface CodeStore {

    /** Grava o código com o TTL do propósito e zera as tentativas. */
    void put(CodePurpose purpose, String email, String code);

    Optional<String> find(CodePurpose purpose, String email);

    /** Soma uma tentativa e devolve o total. */
    long registerAttempt(CodePurpose purpose, String email);

    /** Remove o código e as tentativas. */
    void delete(CodePurpose purpose, String email);
}
