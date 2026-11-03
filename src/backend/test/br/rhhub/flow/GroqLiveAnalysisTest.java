package br.rhhub.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.rhhub.application.port.out.AiProvider;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.Gender;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeFile;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import br.rhhub.infra.document.PdfDocxTextExtractor;
import br.rhhub.infra.llm.OpenAiCompatibleProvider;
import br.rhhub.service.AnalyzeResumeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Mono;

/**
 * Análise de verdade contra o Groq, com a chave da variável de ambiente {@code groq_key} (ou GROQ_API_KEY).
 * É PULADO quando não há chave ou quando api.groq.com não está acessível (por exemplo, rede bloqueada).
 * Se o host responde mas a chave é recusada, o teste FALHA: não é motivo para pular.
 *
 * <p>Para rodar: {@code groq_key=gsk_... mvn test -Dtest=GroqLiveAnalysisTest}. Gasta algumas chamadas da cota.
 */
class GroqLiveAnalysisTest {

    private static final String BASE_URL = "https://api.groq.com/openai/v1";
    private static final List<String> PREFERRED = List.of("llama-3.3-70b-versatile", "openai/gpt-oss-120b",
            "openai/gpt-oss-20b", "llama-3.1-8b-instant");

    private static String key;
    private static String model;
    private static String prompt;

    @BeforeAll
    static void preflight() throws Exception {
        key = System.getenv("groq_key") != null ? System.getenv("groq_key") : System.getenv("GROQ_API_KEY");
        if (key == null || key.isBlank()) {
            System.out.println("GROQ-LIVE PULADO: sem groq_key/GROQ_API_KEY no ambiente");
        }
        assumeTrue(key != null && !key.isBlank(), "Sem groq_key/GROQ_API_KEY no ambiente: teste real do Groq pulado");

        HttpResponse<String> response;
        try {
            response = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(10)).build()
                    .send(HttpRequest.newBuilder(URI.create(BASE_URL + "/models")).timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Bearer " + key.strip()).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            System.out.println("GROQ-LIVE PULADO: api.groq.com inacessível daqui (" + e.getClass().getSimpleName() + ")");
            assumeTrue(false, "api.groq.com inacessível daqui (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "): teste real do Groq pulado");
            return;
        }
        assertThat(response.statusCode()).as("Groq recusou a chave ou a chamada (HTTP %s)", response.statusCode()).isEqualTo(200);

        List<String> available = new ArrayList<>();
        for (JsonNode m : new ObjectMapper().readTree(response.body()).path("data")) {
            available.add(m.path("id").asText());
        }
        String wanted = System.getenv("GROQ_MODEL");
        model = wanted != null && !wanted.isBlank() ? wanted
                : PREFERRED.stream().filter(available::contains).findFirst().orElseThrow(
                        () -> new AssertionError("Nenhum modelo conhecido disponível no Groq: " + available));
        prompt = new String(new ClassPathResource("prompt/resume_analysis.md").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("GROQ-LIVE modelo: " + model);
    }

    private record Outcome(ResumeStatus status, String reason, EmailMessage email) {
    }

    private Outcome analyse(String candidateName, String resumeText) throws Exception {
        var resumes = mock(ResumeRepository.class);
        var vacancies = mock(VacancyRepository.class);
        var users = mock(UserRepository.class);
        var emails = mock(EmailPublisher.class);
        byte[] file = pdf(resumeText);
        Resume pending = new Resume(1L, 2L, 3L, "cv.pdf", "application/pdf", ResumeStatus.PENDING, null, Instant.now());
        when(resumes.findById(1L)).thenReturn(Mono.just(pending));
        when(resumes.findFile(1L)).thenReturn(Mono.just(new ResumeFile(file, "cv.pdf", "application/pdf")));
        when(vacancies.findById(3L)).thenReturn(Mono.just(new Vacancy(3L, "Desenvolvedor(a) Backend Java Pleno",
                "Desenvolver APIs REST com Java 21 e Spring Boot, mensageria com Kafka e banco PostgreSQL. "
                        + "Obrigatório: 3+ anos de experiência com Java e Spring Boot. Desejável: Kafka, Docker.", 1L, Instant.now())));
        when(users.findById(2L)).thenReturn(Mono.just(new User(2L, UUID.randomUUID(), candidateName,
                "candidato" + UUID.randomUUID().toString().substring(0, 6) + "@gmail.com", "12345678901", 30, Gender.OTHER,
                "h", false, Role.USER, AuthProvider.LOCAL, null, Instant.now())));
        var status = new AtomicReference<ResumeStatus>();
        var reason = new AtomicReference<String>();
        when(resumes.updateResult(eq(1L), any(), any())).thenAnswer(inv -> {
            status.set(inv.getArgument(1));
            reason.set(inv.getArgument(2));
            return Mono.just(pending);
        });
        var email = new AtomicReference<EmailMessage>();
        when(emails.publish(eq(1L), any())).thenAnswer(inv -> {
            email.set(inv.getArgument(1));
            return Mono.empty();
        });
        AiProvider groq = new OpenAiCompatibleProvider("groq", key.strip(), model, BASE_URL, Duration.ofSeconds(90));
        var service = new AnalyzeResumeService(resumes, vacancies, users, new PdfDocxTextExtractor(), groq, emails, prompt);

        try {
            service.execute(1L).block(Duration.ofMinutes(3));
        } catch (AiProvider.AiException e) { // limite de taxa do plano gratuito: uma segunda chance
            Thread.sleep(20_000);
            service.execute(1L).block(Duration.ofMinutes(3));
        }
        System.out.println("GROQ-LIVE " + candidateName + " -> " + status.get() + " | " + email.get().subject());
        return new Outcome(status.get(), reason.get(), email.get());
    }

    @Test
    void strongMatchIsApprovedWithAProperEmail() throws Exception {
        var outcome = analyse("Ana Souza", "Ana Souza - Engenheira de Software. Experiencia: 6 anos como desenvolvedora backend. "
                + "Atuei com Java 17 e 21 e Spring Boot em APIs REST de alto volume, mensageria com Apache Kafka, "
                + "PostgreSQL e Docker. Lider tecnica de um time de 5 pessoas. Formacao: Ciencia da Computacao (USP).");

        assertThat(outcome.status()).isEqualTo(ResumeStatus.APROVED);
        assertThat(outcome.email().to()).endsWith("@gmail.com");
        assertThat(outcome.email().subject()).isNotBlank().doesNotContain("|").doesNotContain("\n");
        assertThat(outcome.email().content().toLowerCase()).contains("<html");
        assertThat(outcome.reason()).startsWith(outcome.email().subject()).doesNotContain("<html");
    }

    @Test
    void unrelatedProfileIsRefusedWithAProperEmail() throws Exception {
        var outcome = analyse("Bruno Costa", "Bruno Costa - Chef de cozinha. 8 anos em restaurantes, gestao de equipe de cozinha, "
                + "controle de estoque e cardapios sazonais. Nenhuma experiencia com programacao ou tecnologia.");

        assertThat(outcome.status()).isEqualTo(ResumeStatus.RECUSE);
        assertThat(outcome.email().subject()).isNotBlank().doesNotContain("|").doesNotContain("\n");
        assertThat(outcome.email().content().toLowerCase()).contains("<html");
    }

    private static byte[] pdf(String text) throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var page = new PDPage();
            doc.addPage(page);
            try (var content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(font, 10);
                content.setLeading(14);
                content.newLineAtOffset(40, 750);
                for (String line : text.split("(?<=\\G.{90})")) {
                    content.showText(line.replace("\n", " "));
                    content.newLine();
                }
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
