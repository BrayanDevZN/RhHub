package br.rhhub.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.service.Fakes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PendingResumeRepublisherTest {

    private static final Instant CREATED = Instant.parse("2026-11-03T12:00:00Z"); // o createdAt que o Fakes.Resumes grava

    private final Fakes.Resumes resumes = new Fakes.Resumes();
    private final List<Long> sent = new ArrayList<>();
    private boolean failing;

    private PendingResumeRepublisher at(String time) {
        return new PendingResumeRepublisher(resumes, id -> {
            if (failing) {
                return Mono.error(new IllegalStateException("kafka fora"));
            }
            sent.add(id);
            return Mono.empty();
        }, Clock.fixed(Instant.parse(time), ZoneOffset.UTC)) {
            // cada teste usa a mesma instância entre rodadas, então o relógio vem de fora
        };
    }

    private Resume pending() {
        return resumes.create(new Resume(null, 1L, 1L, "a.pdf", "application/pdf", ResumeStatus.PENDING, null, null), new byte[] {1}).block();
    }

    @Test
    void recentResumesAreLeftAloneAndOldPendingOnesGoBackToTheQueue() {
        var resume = pending();
        assertThat(at("2026-11-03T12:02:00Z").republishDue().block()).isZero(); // 2 min: ainda pode estar sendo analisada
        assertThat(sent).isEmpty();
        assertThat(at("2026-11-03T12:03:00Z").republishDue().block()).isEqualTo(1L); // 3 min: reenfileira
        assertThat(sent).containsExactly(resume.id());
    }

    @Test
    void finishedResumesAreNeverRepublished() {
        var done = pending();
        resumes.updateResult(done.id(), ResumeStatus.APROVED, "ok").block();
        pending();
        assertThat(at("2026-11-03T13:00:00Z").republishDue().block()).isEqualTo(1L);
        assertThat(sent).doesNotContain(done.id());
    }

    @Test
    void eachResumeIsRetriedAtMostOnceEveryTenMinutes() {
        pending();
        var republisher = new PendingResumeRepublisher(resumes, id -> { sent.add(id); return Mono.empty(); },
                new MutableClock(Instant.parse("2026-11-03T12:05:00Z")));
        assertThat(republisher.republishDue().block()).isEqualTo(1L);
        assertThat(republisher.republishDue().block()).isZero(); // logo em seguida: em período de espera
        assertThat(sent).hasSize(1);
    }

    @Test
    void aFailingQueueDoesNotBreakTheRoundAndTheNextOneTriesAgainAfterTheCooldown() {
        pending();
        failing = true;
        var clock = new MutableClock(Instant.parse("2026-11-03T12:05:00Z"));
        var republisher = new PendingResumeRepublisher(resumes, id -> {
            if (failing) return Mono.error(new IllegalStateException("kafka fora"));
            sent.add(id);
            return Mono.empty();
        }, clock);
        assertThat(republisher.republishDue().block()).isZero(); // falhou, mas não derrubou a rodada
        failing = false;
        clock.set(Instant.parse("2026-11-03T12:16:00Z")); // passou o período de espera
        assertThat(republisher.republishDue().block()).isEqualTo(1L);
    }

    /** Relógio que o teste avança. */
    static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void set(Instant next) { now = next; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
