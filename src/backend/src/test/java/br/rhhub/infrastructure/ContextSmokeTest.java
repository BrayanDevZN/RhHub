package br.rhhub.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.rhhub.application.port.in.DeliverEmail;
import br.rhhub.application.port.out.AiProvider;
import br.rhhub.application.port.out.CodeStore;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.application.port.out.ResumeAnalysisPublisher;
import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.application.port.out.TokenService;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.test.context.TestPropertySource;

/**
 * Sobe a aplicação inteira para conferir a ligação dos beans (Postgres + Flyway + Redis reais).
 * O Kafka não precisa estar de pé: listeners ficam desligados e a criação de topics só registra erro.
 */
@EnabledIf("br.rhhub.infrastructure.ContextSmokeTest#servicesUp")
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/rhhub_test",
        "spring.datasource.username=rhhub",
        "spring.datasource.password=rhhub",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.fail-fast=false",
        "spring.kafka.admin.properties.default.api.timeout.ms=1000",
        "spring.kafka.admin.properties.request.timeout.ms=1000",
        "spring.kafka.admin.properties.socket.connection.setup.timeout.max.ms=1000",
        "app.jwt.secret=segredo-de-teste-com-mais-de-32-bytes!!",
        "app.environment=test",
        "app.mail.enabled=false"
})
class ContextSmokeTest {

    static boolean servicesUp() {
        return TestSupport.postgresUp() && TestSupport.redisUp();
    }

    @Autowired
    ApplicationContext ctx;

    @Test
    void wiresEveryPortAndKafkaPieces() {
        for (Class<?> port : new Class<?>[] {CodeStore.class, TokenService.class, ResumeRepository.class,
                EmailPublisher.class, ResumeAnalysisPublisher.class, AiProvider.class, DeliverEmail.class,
                CommonErrorHandler.class}) {
            assertThat(ctx.getBeansOfType(port)).as(port.getSimpleName()).hasSize(1);
        }
        assertThat(ctx.getBeansOfType(NewTopic.class).values()).extracting(NewTopic::name)
                .containsExactlyInAnyOrder("email.send", "email.send.DLT", "resume.analysis", "resume.analysis.DLT");
    }
}
