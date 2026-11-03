package br.rhhub.application.port.out;

import reactor.core.publisher.Mono;

/** Login com Google (OAuth2, fluxo de código). Desligado por padrão: enabled() devolve false. */
public interface GoogleIdentityProvider {

    boolean enabled();

    /** URL do Google para onde o usuário é enviado. state é o valor anti-CSRF que o controller confere na volta. */
    String authorizationUrl(String state);

    /** Troca o code devolvido pelo Google pelo perfil do usuário. Erro Unauthorized se o Google recusar. */
    Mono<Profile> fetchProfile(String code);

    record Profile(String sub, String email, boolean emailVerified, String name) {
    }
}
