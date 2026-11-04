package br.rhhub.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Quem avisa dentro do app. Avisar nunca derruba o fluxo que o originou: as implementações engolem o erro (e o
 * registram), então o Mono devolvido sempre termina vazio.
 */
public interface Notifier {

    /** Não avisa ninguém (para quem monta os serviços sem o sino). */
    Notifier NONE = new Notifier() {
        @Override
        public Mono<Void> user(Long userId, String title, String message, String link) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> admins(String title, String message, String link) {
            return Mono.empty();
        }
    };

    Mono<Void> user(Long userId, String title, String message, String link);

    /** Todos os administradores. */
    Mono<Void> admins(String title, String message, String link);
}
