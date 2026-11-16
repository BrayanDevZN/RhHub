package br.rhhub.infra.messaging;

import br.rhhub.application.port.out.ResumeAnalysisPublisher;
import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Rede de segurança do envio de currículo: o envio responde sem esperar o Kafka, então se o aviso à fila falhar a
 * candidatura fica pending. A cada minuto, no serviço consumer (o que já tem conexão com o Kafka), as que estão
 * pending há mais de 3 minutos voltam para a fila, no máximo uma vez a cada 10 minutos cada. Reenviar é seguro: a
 * análise ignora candidatura que já saiu de pending. Não roda na API (KAFKA_CONSUMERS_ENABLED=false).
 */
@Component
@ConditionalOnProperty(name = "app.kafka.consumers-enabled", havingValue = "true", matchIfMissing = true)
public class PendingResumeRepublisher {

    static final Duration MIN_AGE = Duration.ofMinutes(3);
    static final Duration COOLDOWN = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(PendingResumeRepublisher.class);

    private final ResumeRepository resumes;
    private final ResumeAnalysisPublisher publisher;
    private final Clock clock;
    private final Map<Long, Instant> lastAttempt = new ConcurrentHashMap<>();

    public PendingResumeRepublisher(ResumeRepository resumes, ResumeAnalysisPublisher publisher, Clock clock) {
        this.resumes = resumes;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    void run() {
        republishDue().subscribe(
                count -> {
                    if (count > 0) {
                        log.info("{} candidatura(s) pending reenfileirada(s) para análise", count);
                    }
                },
                error -> log.warn("Falha ao reenfileirar candidaturas pending: {}", error.toString()));
    }

    /** Quantas foram reenfileiradas nesta rodada. */
    Mono<Long> republishDue() {
        Instant now = clock.instant();
        return resumes.findAll(ResumeStatus.PENDING, null)
                .collectList()
                .flatMap(pending -> {
                    lastAttempt.keySet().retainAll(pending.stream().map(Resume::id).toList()); // esquece as que já saíram de pending
                    return reactor.core.publisher.Flux.fromIterable(pending)
                            .filter(r -> r.createdAt() != null && Duration.between(r.createdAt(), now).compareTo(MIN_AGE) >= 0)
                            .filter(r -> {
                                Instant last = lastAttempt.get(r.id());
                                return last == null || Duration.between(last, now).compareTo(COOLDOWN) >= 0;
                            })
                            .concatMap(r -> {
                                lastAttempt.put(r.id(), now);
                                return publisher.publish(r.id()).thenReturn(1L).onErrorResume(error -> {
                                    log.warn("Candidatura {}: reenfileirar falhou: {}", r.id(), error.toString());
                                    return Mono.empty();
                                });
                            })
                            .count();
                });
    }
}
