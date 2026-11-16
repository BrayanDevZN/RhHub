package br.rhhub.application.web;

import br.rhhub.application.port.out.EmailSender;
import br.rhhub.domain.exception.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Todo erro vira JSON {status, error, message, path} com o código certo (seção 4.11). Erro inesperado vira 500 sem
 * detalhe interno na resposta; o detalhe fica só no log.
 */
@Component
@Order(-2)
public class ApiExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ObjectMapper mapper;

    public ApiExceptionHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(error);
        }
        HttpStatusCode status;
        String message;
        if (error instanceof DomainException domain) {
            status = statusOf(domain);
            message = domain.getMessage();
        } else if (error instanceof EmailSender.EmailException) {
            log.error("Falha no envio de e-mail: {}", error.getMessage());
            status = HttpStatus.BAD_GATEWAY;
            message = "Não foi possível enviar o e-mail agora. Tente de novo.";
        } else if (error instanceof ResponseStatusException rse) {
            status = rse.getStatusCode();
            message = status.is4xxClientError() ? friendly(status) : "Erro inesperado.";
        } else if (error instanceof DecodingException) {
            status = HttpStatus.BAD_REQUEST;
            message = "Corpo da requisição inválido.";
        } else {
            log.error("Erro inesperado em {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), error);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Erro inesperado. Tente de novo.";
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var body = new LinkedHashMap<String, Object>();
        body.put("status", status.value());
        body.put("error", HttpStatus.resolve(status.value()) != null ? HttpStatus.resolve(status.value()).getReasonPhrase() : "Error");
        body.put("message", message);
        body.put("path", exchange.getRequest().getPath().value());
        try {
            byte[] bytes = mapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return response.writeWith(Mono.just(response.bufferFactory().wrap(
                    "{\"status\":500}".getBytes(StandardCharsets.UTF_8))));
        }
    }

    static HttpStatus statusOf(DomainException e) {
        if (e instanceof DomainException.Invalid) {
            return HttpStatus.BAD_REQUEST;
        } else if (e instanceof DomainException.Unauthorized) {
            return HttpStatus.UNAUTHORIZED;
        } else if (e instanceof DomainException.Forbidden) {
            return HttpStatus.FORBIDDEN;
        } else if (e instanceof DomainException.NotFound) {
            return HttpStatus.NOT_FOUND;
        } else if (e instanceof DomainException.Conflict) {
            return HttpStatus.CONFLICT;
        } else if (e instanceof DomainException.Unprocessable) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        } else if (e instanceof DomainException.TooManyRequests) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String friendly(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "Rota não encontrada.";
            case 405 -> "Método não permitido.";
            case 415 -> "Tipo de conteúdo não suportado.";
            case 413 -> "Arquivo grande demais.";
            default -> "Requisição inválida.";
        };
    }
}
