package br.rhhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.rhhub.application.port.out.AiProvider;
import br.rhhub.application.port.out.DocumentTextExtractor;
import br.rhhub.application.port.out.EmailMessage;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.DocumentType;
import br.rhhub.domain.model.Gender;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeFile;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class AnalyzeResumeServiceTest {

    private static final String HTML = "<html><body><p>Resultado</p></body></html>";
    private static final String PROMPT = "PROMPT-DE-TRIAGEM";

    private final ResumeRepository resumes = mock(ResumeRepository.class);
    private final VacancyRepository vacancies = mock(VacancyRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
    private final AiProvider ai = mock(AiProvider.class);
    private final EmailPublisher emails = mock(EmailPublisher.class);
    private final AnalyzeResumeService service =
            new AnalyzeResumeService(resumes, vacancies, users, extractor, ai, emails, PROMPT);

    private final Resume pending = new Resume(7L, 3L, 5L, "cv.pdf", "application/pdf", ResumeStatus.PENDING, null, Instant.now());
    private final byte[] file = {'%', 'P', 'D', 'F'};

    @BeforeEach
    void setUp() {
        when(resumes.findById(7L)).thenReturn(Mono.just(pending));
        when(vacancies.findById(5L)).thenReturn(Mono.just(new Vacancy(5L, "Dev Java", "Spring e Kafka", 1L, Instant.now())));
        when(users.findById(3L)).thenReturn(Mono.just(new User(3L, UUID.randomUUID(), "Ana Souza", "ana@gmail.com",
                "12345678901", 30, Gender.FEMALE, "h", false, Role.USER, AuthProvider.LOCAL, null, Instant.now())));
        when(resumes.findFile(7L)).thenReturn(Mono.just(new ResumeFile(file, "cv.pdf", "application/pdf")));
        when(extractor.detect(file)).thenReturn(Optional.of(DocumentType.PDF));
        when(extractor.extract(file, DocumentType.PDF)).thenReturn("Dez anos de Java, Spring e Kafka");
        when(resumes.updateResult(eq(7L), any(), any())).thenReturn(Mono.just(pending));
        when(emails.publish(eq(7L), any())).thenReturn(Mono.empty());
    }

    private void aiAnswers(String answer) {
        when(ai.complete(anyString(), anyString())).thenReturn(Mono.just(answer));
    }

    @Test
    void approvedResumeIsSavedAndTheEmailIsQueued() {
        aiAnswers("aproved | Seu processo segue em frente | " + HTML);

        service.execute(7L).block();

        verify(resumes).updateResult(eq(7L), eq(ResumeStatus.APROVED), org.mockito.ArgumentMatchers.startsWith("Seu processo segue em frente"));
        var email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emails).publish(eq(7L), email.capture());
        assertThat(email.getValue().to()).isEqualTo("ana@gmail.com");
        assertThat(email.getValue().subject()).isEqualTo("Seu processo segue em frente");
        assertThat(email.getValue().content()).isEqualTo(HTML);
        assertThat(email.getValue().type()).isEqualTo(EmailMessage.Type.HTML);
    }

    @Test
    void refusedResumeIsSavedAsRecuse() {
        aiAnswers("recuse | Atualização da sua candidatura | " + HTML);
        service.execute(7L).block();
        verify(resumes).updateResult(eq(7L), eq(ResumeStatus.RECUSE), anyString());
        verify(emails).publish(eq(7L), any());
    }

    @Test
    void theAiReceivesThePromptTheVacancyTheCandidateAndTheResumeText() {
        aiAnswers("aproved | S | " + HTML);
        service.execute(7L).block();

        var input = ArgumentCaptor.forClass(String.class);
        verify(ai).complete(eq(PROMPT), input.capture());
        assertThat(input.getValue()).contains("JOB_DESCRIPTION:", "Title: Dev Java", "Spring e Kafka",
                "CANDIDATE_NAME: Ana Souza", "CANDIDATE_RESUME:", "Dez anos de Java, Spring e Kafka");
    }

    @Test
    void resumeAlreadyDecidedOrMissingIsIgnored() {
        when(resumes.findById(7L)).thenReturn(Mono.just(new Resume(7L, 3L, 5L, "cv.pdf", "application/pdf",
                ResumeStatus.APROVED, "ok", Instant.now())));
        service.execute(7L).block();
        when(resumes.findById(8L)).thenReturn(Mono.empty());
        service.execute(8L).block();

        verify(ai, never()).complete(anyString(), anyString());
        verify(emails, never()).publish(any(), any());
    }

    @Test
    void unreadableFileStillGetsAnAnswerFromTheAi() {
        when(extractor.extract(file, DocumentType.PDF)).thenThrow(new DomainException.Unprocessable("quebrado"));
        aiAnswers("recuse | Atualização | " + HTML);

        service.execute(7L).block();

        var input = ArgumentCaptor.forClass(String.class);
        verify(ai).complete(eq(PROMPT), input.capture());
        assertThat(input.getValue()).contains("[O arquivo enviado está corrompido e não pôde ser lido.]");
        verify(resumes).updateResult(eq(7L), eq(ResumeStatus.RECUSE), anyString());
    }

    @Test
    void unknownFileTypeAndEmptyTextAreReportedToTheAi() {
        when(extractor.detect(file)).thenReturn(Optional.empty());
        aiAnswers("recuse | S | " + HTML);
        service.execute(7L).block();
        var input = ArgumentCaptor.forClass(String.class);
        verify(ai).complete(eq(PROMPT), input.capture());
        assertThat(input.getValue()).contains("não pôde ser identificado como PDF ou DOCX");
    }

    @Test
    void veryLongResumesAreTruncated() {
        when(extractor.extract(file, DocumentType.PDF)).thenReturn("x".repeat(AnalyzeResumeService.MAX_RESUME_CHARS + 5_000));
        aiAnswers("aproved | S | " + HTML);
        service.execute(7L).block();
        var input = ArgumentCaptor.forClass(String.class);
        verify(ai).complete(eq(PROMPT), input.capture());
        assertThat(input.getValue()).contains("[texto truncado por tamanho]").hasSizeLessThan(AnalyzeResumeService.MAX_RESUME_CHARS + 500);
    }

    @Test
    void malformedAiAnswerLeavesTheResumePendingAndQueuesNoEmail() {
        aiAnswers("Claro! Aqui está a análise do candidato.");

        assertThatThrownBy(() -> service.execute(7L).block()).isInstanceOf(DomainException.Unprocessable.class);

        verify(resumes, never()).updateResult(any(), any(), any());
        verify(emails, never()).publish(any(), any());
    }

    @Test
    void aiFailurePropagatesForTheJobToBeRetried() {
        when(ai.complete(anyString(), anyString())).thenReturn(Mono.error(new AiProvider.AiException("fora do ar")));
        assertThatThrownBy(() -> service.execute(7L).block()).isInstanceOf(AiProvider.AiException.class);
        verify(resumes, never()).updateResult(any(), any(), any());
    }

    @Test
    void ifTheEmailCannotBeQueuedTheResumeGoesBackToPending() {
        aiAnswers("aproved | S | " + HTML);
        when(emails.publish(eq(7L), any())).thenReturn(Mono.error(new IllegalStateException("kafka fora")));

        assertThatThrownBy(() -> service.execute(7L).block()).isInstanceOf(IllegalStateException.class);

        verify(resumes).updateResult(eq(7L), eq(ResumeStatus.APROVED), anyString());
        verify(resumes).updateResult(7L, ResumeStatus.PENDING, null); // desfeito, para a repetição analisar de novo
    }

    @Test
    void theAiReceivesRequirementsAndResponsibilitiesWhenTheVacancyHasThem() {
        var user = new br.rhhub.domain.model.User(1L, java.util.UUID.randomUUID(), "Ana", "ana@gmail.com", null, null, null, null,
                false, br.rhhub.domain.model.Role.USER, br.rhhub.domain.model.AuthProvider.LOCAL, null, null);
        var full = new br.rhhub.domain.model.Vacancy(1L, "Dev", "Descrição", "Java e SQL", "Criar APIs", null, null, false);
        String input = AnalyzeResumeService.input(full, user, "meu cv");
        assertThat(input).contains("Title: Dev").contains("Descrição").contains("REQUIREMENTS:\nJava e SQL")
                .contains("RESPONSIBILITIES:\nCriar APIs").contains("CANDIDATE_NAME: Ana").contains("CANDIDATE_RESUME:\nmeu cv");
        var bare = new br.rhhub.domain.model.Vacancy(2L, "QA", "Só descrição", null, null);
        assertThat(AnalyzeResumeService.input(bare, user, "cv")).doesNotContain("REQUIREMENTS").doesNotContain("RESPONSIBILITIES");
    }
}
