package br.rhhub.application.port.out;

import br.rhhub.domain.auth.CodePurpose;
import reactor.core.publisher.Mono;

public interface VerificationCodeStore {

    /**
     * Gera um código novo (substitui o anterior) e o devolve. Erro TooManyRequests se outro foi pedido há menos
     * de um minuto para o mesmo e-mail e finalidade.
     */
    Mono<String> issue(CodePurpose purpose, String email);

    /**
     * Confere o código. Certo: ele é apagado (vale uma vez) e o Mono completa. Errado, expirado ou inexistente:
     * Unauthorized. Na 5ª tentativa errada o código é apagado e vem TooManyRequests.
     */
    Mono<Void> verify(CodePurpose purpose, String email, String code);
}
