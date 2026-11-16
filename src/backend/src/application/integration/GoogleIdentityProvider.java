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

    /**
     * O Google (ou a rede até ele) recusou o login. reason é o código de erro do Google (invalid_client, invalid_grant,
     * redirect_uri_mismatch...) ou google_unreachable. Não tem dado sensível: vai na URL de volta para o front.
     */
    class LoginFailure extends RuntimeException {
        private final String reason;

        public LoginFailure(String reason, Throwable cause) {
            super("Google recusou o login: " + reason, cause);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }
}
