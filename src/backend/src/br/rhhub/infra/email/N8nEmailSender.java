package br.rhhub.infra.email;

import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailSender;
import br.rhhub.domain.exception.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Envia e-mail chamando o webhook do workflow "gmail" do n8n, com um JWT HS256 novo a cada chamada.
 * O n8n responde 200 assim que recebe, antes de validar e enviar; por isso a mensagem é validada aqui.
 */
public class N8nEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(N8nEmailSender.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final WebClient client;
    private final JwtHs256 jwt;
    private final Clock clock;
    private final Duration timeout;

    public N8nEmailSender(String webhookUrl, String jwtSecret, Duration timeout, Clock clock, ObjectMapper mapper) {
        this.jwt = new JwtHs256(jwtSecret, mapper);
        this.clock = clock;
        this.timeout = timeout;
        this.client = WebClient.builder()
                .baseUrl(webhookUrl)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().proxyWithSystemProperties().responseTimeout(timeout)))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Mono<Void> send(EmailMessage message) {
        String problem = validate(message);
        if (problem != null) {
            return Mono.error(new DomainException.Invalid(problem));
        }
        var body = Map.of(
                "type", message.type().name().toLowerCase(Locale.ROOT),
                "to", message.to().trim(),
                "subject", message.subject().trim(),
                "content", message.content().trim());
        return Mono.defer(() -> client.post()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.sign("rhhub", clock.instant(), Duration.ofMinutes(1)))
                        .bodyValue(body)
                        .retrieve()
                        .toBodilessEntity())
                .timeout(timeout)
                .doOnSuccess(response -> log.info("E-mail '{}' entregue ao n8n para {}", message.subject(), message.to()))
                .onErrorMap(e -> new EmailException("Falha ao enviar e-mail pelo n8n: " + e.getMessage(), e))
                .then();
    }

    @Override
    public String name() {
        return "n8n";
    }

    /** Mesmas regras do workflow: type, to (e-mail válido), subject e content obrigatórios. */
    static String validate(EmailMessage m) {
        if (m == null) {
            return "Mensagem de e-mail ausente";
        }
        if (m.type() == null) {
            return "type é obrigatório";
        }
        if (m.to() == null || m.to().isBlank()) {
            return "to é obrigatório";
        }
        if (!EMAIL.matcher(m.to().trim()).matches()) {
            return "to deve ser um e-mail válido";
        }
        if (m.subject() == null || m.subject().isBlank()) {
            return "subject é obrigatório";
        }
        if (m.content() == null || m.content().isBlank()) {
            return "content é obrigatório";
        }
        return null;
    }
}
