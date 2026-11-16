package br.rhhub.flow;

import br.rhhub.testing.FunctionalTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.rhhub.application.port.out.DeadLetterRepository;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailSender;
import br.rhhub.application.port.out.ResumeAnalysisPublisher;
import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.DeadLetterStatus;
import br.rhhub.domain.model.Gender;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.infra.TestSupport;
import br.rhhub.infra.config.AppProperties;
import br.rhhub.infra.config.ClockConfig;
import br.rhhub.infra.config.ServiceConfig;
import br.rhhub.infra.database.DatabaseUrl;
import br.rhhub.infra.database.SqlExecutor;
import br.rhhub.infra.database.SqliteSqlExecutor;
import br.rhhub.infra.database.adapter.DeadLetterRepositoryAdapter;
import br.rhhub.infra.database.adapter.ProfilePhotoRepositoryAdapter;
import br.rhhub.infra.database.adapter.ResumeRepositoryAdapter;
import br.rhhub.infra.database.adapter.UserRepositoryAdapter;
import br.rhhub.infra.database.adapter.VacancyRepositoryAdapter;
import br.rhhub.infra.database.table.Dialect;
import br.rhhub.infra.database.table.TableCreator;
import br.rhhub.infra.document.PdfDocxTextExtractor;
import br.rhhub.infra.llm.LlmConfig;
import br.rhhub.infra.messaging.AnalysisConsumer;
import br.rhhub.infra.messaging.EmailConsumer;
import br.rhhub.infra.messaging.KafkaConfig;
import br.rhhub.infra.messaging.KafkaEmailPublisher;
import br.rhhub.infra.messaging.KafkaPublisher;
import br.rhhub.infra.messaging.KafkaResumeAnalysisPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import reactor.core.publisher.Mono;

/**
 * O fluxo inteiro, sem mocks do nosso código: banco SQLite com as tabelas criadas pelo TableCreator, currículo em PDF
 * e DOCX de verdade, Kafka de verdade (broker embutido), consumers, serviço de análise, cliente HTTP da LLM e
 * repetição/DLQ.
 *
 * <p>ATENÇÃO: a LLM aqui é um servidor local que fala o protocolo /chat/completions (OpenAI/Groq). Ele prova que o
 * nosso cliente, o prompt e o parser funcionam ponta a ponta, mas NÃO é uma resposta de modelo real. O teste com o
 * Groq de verdade é o GroqLiveAnalysisTest. O envio de e-mail também é um gravador: nada sai para ninguém.
 */
@FunctionalTest
class AnalysisFlowIntegrationTest {

    private static final String HTML = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>t</title></head>"
            + "<body><p>Olá, %s.</p><p>%s</p></body></html>";

    private static EmbeddedKafkaKraftBroker broker;
    private static HttpServer llm;
    private static Path dir;
    private static SqliteSqlExecutor sql;
    private static AnnotationConfigApplicationContext ctx;

    private static final ObjectMapper mapper = new ObjectMapper();
    /** O que o "provedor" recebeu: authorization + corpo de cada chamada. */
    private static final List<JsonNode> llmRequests = new CopyOnWriteArrayList<>();
    private static final List<String> llmAuth = new CopyOnWriteArrayList<>();
    private static final List<EmailMessage> sentEmails = new CopyOnWriteArrayList<>();

    @Configuration
    @Import({KafkaConfig.class, KafkaPublisher.class, KafkaResumeAnalysisPublisher.class, KafkaEmailPublisher.class,
            AnalysisConsumer.class, EmailConsumer.class, ServiceConfig.class, LlmConfig.class, ClockConfig.class,
            PdfDocxTextExtractor.class, UserRepositoryAdapter.class, VacancyRepositoryAdapter.class,
            ResumeRepositoryAdapter.class, DeadLetterRepositoryAdapter.class, ProfilePhotoRepositoryAdapter.class,
            br.rhhub.infra.database.adapter.NotificationRepositoryAdapter.class,
            br.rhhub.infra.database.adapter.VacancyPhotoRepositoryAdapter.class,
            br.rhhub.infra.security.AuthConfig.class, br.rhhub.infra.security.BcryptPasswordHasher.class,
            br.rhhub.infra.email.HtmlEmailComposer.class, br.rhhub.infra.google.GoogleConfig.class})
    static class Context {

        @Bean
        AppProperties appProperties() {
            var base = TestSupport.props();
            return new AppProperties("production", base.database(), base.redis(),
                    new AppProperties.Kafka(broker.getBrokersAsString(), "rhhub", null, 3, Duration.ofMillis(50),
                            "email.send", "resume.analysis"),
                    new AppProperties.Ai(null, "m", "http://x", "chave-de-teste", "modelo-de-teste",
                            "http://127.0.0.1:" + llm.getAddress().getPort(), Duration.ofSeconds(10)),
                    base.email());
        }

        @Bean
        br.rhhub.infra.config.AuthProperties authProperties() {
            return new br.rhhub.infra.config.AuthProperties(null, null, null, 60, 1000, null);
        }

        /** O Redis não participa deste fluxo: as portas que o usam entram como dublês. */
        @Bean
        br.rhhub.application.port.out.RefreshTokenStore refreshTokenStore() {
            return org.mockito.Mockito.mock(br.rhhub.application.port.out.RefreshTokenStore.class);
        }

        @Bean
        br.rhhub.application.port.out.VerificationCodeStore verificationCodeStore() {
            return org.mockito.Mockito.mock(br.rhhub.application.port.out.VerificationCodeStore.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return mapper;
        }

        @Bean
        SqlExecutor sqlExecutor() {
            return sql;
        }

        @Bean(initMethod = "create")
        TableCreator tableCreator(SqlExecutor executor) {
            return new TableCreator(executor, Dialect.SQLITE);
        }

        /** O envio de verdade (n8n) é trocado por um gravador. */
        @Bean
        EmailSender emailSender() {
            return new EmailSender() {
                @Override
                public Mono<Void> send(EmailMessage message) {
                    sentEmails.add(message);
                    return Mono.empty();
                }

                @Override
                public String name() {
                    return "gravador";
                }
            };
        }
    }

    @BeforeAll
    static void start() throws Exception {
        llm = fakeChatCompletions();
        dir = Files.createTempDirectory("rhhub-flow");
        sql = new SqliteSqlExecutor(DatabaseUrl.parse("none", dir.resolve("flow.db").toString()));
        broker = new EmbeddedKafkaKraftBroker(1, 3);
        broker.afterPropertiesSet();
        ctx = new AnnotationConfigApplicationContext(Context.class);
        await().atMost(Duration.ofSeconds(60)).until(() -> br.rhhub.infra.TestSupport.allConsumersAssigned(
                ctx.getBean(org.springframework.kafka.config.KafkaListenerEndpointRegistry.class)));
    }

    @AfterAll
    static void stop() throws Exception {
        ctx.close();
        broker.destroy();
        llm.stop(0);
        sql.close();
        try (var files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    /** Servidor /chat/completions: aprova quem tem Java no currículo, recusa os outros e responde lixo se pedido. */
    private static HttpServer fakeChatCompletions() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            llmRequests.add(body);
            llmAuth.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            String user = body.path("messages").path(1).path("content").asText();
            String name = user.lines().filter(l -> l.startsWith("CANDIDATE_NAME: ")).findFirst().orElse("").substring(16);
            // decide só pelo currículo: a descrição da vaga também fala em Java
            String resume = user.substring(user.indexOf("CANDIDATE_RESUME:"));
            String answer;
            if (resume.contains("FORMATO_INVALIDO")) {
                answer = "Claro! Aqui está a análise do candidato, sem seguir o formato.";
            } else if (resume.contains("Java")) {
                answer = "aproved | Seu processo segue para a próxima etapa | " + HTML.formatted(name, "Seu perfil combina com a vaga.");
            } else {
                answer = "recuse | Atualização da sua candidatura | " + HTML.formatted(name, "Neste momento seguiremos com outros perfis.");
            }
            byte[] out = mapper.writeValueAsBytes(java.util.Map.of("choices",
                    List.of(java.util.Map.of("message", java.util.Map.of("role", "assistant", "content", answer)))));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return server;
    }

    // ---------- dados e arquivos

    private record Scenario(User user, Vacancy vacancy, Resume resume) {
    }

    private Scenario scenario(String name, String vacancyName, byte[] file, String fileName, String contentType) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User user = ctx.getBean(UserRepository.class).save(new User(null, null, name, unique + "@gmail.com", null, null,
                Gender.OTHER, "hash", false, Role.USER, AuthProvider.LOCAL, null, null)).block();
        Vacancy vacancy = ctx.getBean(VacancyRepository.class)
                .save(new Vacancy(null, vacancyName + " " + unique, "Backend com Java, Spring e Kafka", user.id(), null)).block();
        Resume resume = ctx.getBean(ResumeRepository.class).create(new Resume(null, user.id(), vacancy.id(), fileName,
                contentType, ResumeStatus.PENDING, null, null), file).block();
        return new Scenario(user, vacancy, resume);
    }

    private static byte[] pdf(String text) throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage();
            doc.addPage(page);
            try (var content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx(String text) throws Exception {
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private Resume stored(Long id) {
        return ctx.getBean(ResumeRepository.class).findById(id).block();
    }

    private List<EmailMessage> emailsTo(String address) {
        return sentEmails.stream().filter(e -> e.to().equals(address)).toList();
    }

    // ---------- cenários

    @Test
    void strongPdfResumeIsApprovedAndTheCandidateGetsTheEmail() throws Exception {
        var s = scenario("Ana Souza", "Dev Java", pdf("Dez anos de experiencia com Java e Spring"), "ana.pdf", "application/pdf");

        ctx.getBean(ResumeAnalysisPublisher.class).publish(s.resume().id()).block();

        await().atMost(Duration.ofSeconds(30)).until(() -> !emailsTo(s.user().email()).isEmpty());
        Resume done = stored(s.resume().id());
        assertThat(done.status()).isEqualTo(ResumeStatus.APROVED);
        assertThat(done.reason()).startsWith("Seu processo segue para a próxima etapa")
                .contains("Olá, Ana Souza.").contains("Seu perfil combina com a vaga.").doesNotContain("<");

        EmailMessage email = emailsTo(s.user().email()).get(0);
        assertThat(email.subject()).isEqualTo("Seu processo segue para a próxima etapa");
        assertThat(email.type()).isEqualTo(EmailMessage.Type.HTML);
        assertThat(email.content()).startsWith("<!DOCTYPE html>").contains("Olá, Ana Souza.");
    }

    @Test
    void whatTheLlmReceivesIsThePromptTheVacancyAndTheTextExtractedFromTheFile() throws Exception {
        var s = scenario("Beto Lima", "Dev Java", pdf("Especialista em Java e Kafka"), "beto.pdf", "application/pdf");
        ctx.getBean(ResumeAnalysisPublisher.class).publish(s.resume().id()).block();
        await().atMost(Duration.ofSeconds(30)).until(() -> !emailsTo(s.user().email()).isEmpty());

        JsonNode request = llmRequests.stream()
                .filter(r -> r.path("messages").path(1).path("content").asText().contains("CANDIDATE_NAME: Beto Lima"))
                .findFirst().orElseThrow();
        assertThat(request.path("model").asText()).isEqualTo("modelo-de-teste");
        String system = request.path("messages").path(0).path("content").asText();
        assertThat(system).contains("# Role and mission").contains("DECISION | SUBJECT_TEXT | COMPLETE_BODY_HTML_DOCUMENT");
        String user = request.path("messages").path(1).path("content").asText();
        assertThat(user).contains("JOB_DESCRIPTION:", "Title: " + s.vacancy().name(), "Backend com Java, Spring e Kafka",
                "CANDIDATE_RESUME:", "Especialista em Java e Kafka"); // texto lido do PDF
        assertThat(llmAuth).contains("Bearer chave-de-teste");
    }

    @Test
    void unrelatedDocxResumeIsRefusedAndTheCandidateStillGetsTheEmail() throws Exception {
        var s = scenario("Carla Dias", "Dev Java", docx("Chef de cozinha com 8 anos em restaurantes"), "carla.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        ctx.getBean(ResumeAnalysisPublisher.class).publish(s.resume().id()).block();

        await().atMost(Duration.ofSeconds(30)).until(() -> !emailsTo(s.user().email()).isEmpty());
        assertThat(stored(s.resume().id()).status()).isEqualTo(ResumeStatus.RECUSE);
        assertThat(emailsTo(s.user().email()).get(0).subject()).isEqualTo("Atualização da sua candidatura");
    }

    @Test
    void corruptFileDoesNotKillTheJobTheLlmIsToldAndRefuses() {
        var s = scenario("Davi Reis", "Dev Java", "%PDF-isso nao e um pdf de verdade".getBytes(StandardCharsets.UTF_8),
                "davi.pdf", "application/pdf");

        ctx.getBean(ResumeAnalysisPublisher.class).publish(s.resume().id()).block();

        await().atMost(Duration.ofSeconds(30)).until(() -> !emailsTo(s.user().email()).isEmpty());
        assertThat(stored(s.resume().id()).status()).isEqualTo(ResumeStatus.RECUSE);
        JsonNode request = llmRequests.stream()
                .filter(r -> r.path("messages").path(1).path("content").asText().contains("CANDIDATE_NAME: Davi Reis"))
                .findFirst().orElseThrow();
        assertThat(request.path("messages").path(1).path("content").asText()).contains("corrompido");
    }

    @Test
    void malformedLlmAnswerIsRetriedThenGoesToTheDlqAndTheResumeStaysPending() throws Exception {
        var s = scenario("Eva Melo", "Dev Java", pdf("FORMATO_INVALIDO Java"), "eva.pdf", "application/pdf");

        ctx.getBean(ResumeAnalysisPublisher.class).publish(s.resume().id()).block();

        var dlq = ctx.getBean(DeadLetterRepository.class);
        await().atMost(Duration.ofSeconds(60)).until(
                () -> !dlq.findByKey("resume.analysis", String.valueOf(s.resume().id())).collectList().block().isEmpty());
        var saved = dlq.findByKey("resume.analysis", String.valueOf(s.resume().id())).blockFirst();
        assertThat(saved.status()).isEqualTo(DeadLetterStatus.FAILED);
        assertThat(saved.attempts()).isEqualTo(4);
        assertThat(saved.errorType()).isEqualTo("br.rhhub.domain.exception.DomainException$Unprocessable");
        assertThat(saved.payload()).isEqualTo("{\"resumeId\":" + s.resume().id() + "}");

        assertThat(stored(s.resume().id()).status()).isEqualTo(ResumeStatus.PENDING);
        assertThat(emailsTo(s.user().email())).isEmpty();
        long calls = llmRequests.stream()
                .filter(r -> r.path("messages").path(1).path("content").asText().contains("CANDIDATE_NAME: Eva Melo")).count();
        assertThat(calls).isEqualTo(4); // 1 + 3 repetições
    }

    @Test
    void publishingTheSameResumeTwiceAnalysesAndEmailsOnlyOnce() throws Exception {
        var s = scenario("Fabio Nunes", "Dev Java", pdf("Java Spring"), "fabio.pdf", "application/pdf");
        var publisher = ctx.getBean(ResumeAnalysisPublisher.class);

        publisher.publish(s.resume().id()).block();
        await().atMost(Duration.ofSeconds(30)).until(() -> !emailsTo(s.user().email()).isEmpty());
        publisher.publish(s.resume().id()).block();
        Thread.sleep(3000);

        assertThat(emailsTo(s.user().email())).hasSize(1);
        long calls = llmRequests.stream()
                .filter(r -> r.path("messages").path(1).path("content").asText().contains("CANDIDATE_NAME: Fabio Nunes")).count();
        assertThat(calls).isEqualTo(1);
    }
}
