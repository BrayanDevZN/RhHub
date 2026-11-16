package br.rhhub.application.web;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Filtro que coloca cabeçalhos de segurança em toda resposta (nosniff, sem iframe, sem referrer). */
@Component
public class SecurityHeadersFilter implements WebFilter, Ordered {

    /**
     * Posição na fila: a primeira de todas, para o cabeçalho sair até em respostas de erro.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Acrescenta à resposta: X-Content-Type-Options nosniff (o navegador não adivinha o tipo do arquivo), X-Frame-Options DENY (a página não abre dentro de iframe) e Referrer-Policy no-referrer (não vaza a URL de origem).
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var headers = exchange.getResponse().getHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        return chain.filter(exchange);
    }
}
