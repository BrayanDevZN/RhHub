package br.rhhub.infra.config;

import br.rhhub.application.port.in.AnalyzeResume;
import br.rhhub.application.port.in.DeliverEmail;
import br.rhhub.application.port.out.AiProvider;
import br.rhhub.application.port.out.DocumentTextExtractor;
import br.rhhub.application.port.out.EmailComposer;
import br.rhhub.application.port.out.EmailPublisher;
import br.rhhub.application.port.out.GoogleIdentityProvider;
import br.rhhub.application.port.out.RefreshTokenStore;
import br.rhhub.application.port.out.VerificationCodeStore;
import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.auth.JwtService;
import br.rhhub.domain.auth.PasswordHasher;
import br.rhhub.service.AuthService;
import br.rhhub.service.UserService;
import java.time.Clock;
import br.rhhub.application.port.out.EmailSender;
import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.application.port.out.UserRepository;
import br.rhhub.application.port.out.VacancyRepository;
import br.rhhub.service.AnalyzeResumeService;
import br.rhhub.service.DeliverEmailService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Monta os serviços, que não conhecem o Spring, com as implementações da infra. */
@Configuration
public class ServiceConfig {

    @Bean
    DeliverEmail deliverEmail(EmailSender sender) {
        return new DeliverEmailService(sender);
    }

    @Bean
    AuthService authService(UserRepository users, PasswordHasher hasher, JwtService jwt, RefreshTokenStore refresh,
            VerificationCodeStore codes, EmailComposer composer, EmailSender sender, GoogleIdentityProvider google,
            Clock clock, AppProperties props) {
        return new AuthService(users, hasher, jwt, refresh, codes, composer, sender, google, clock, props.isTest());
    }

    @Bean
    UserService userService(UserRepository users, PasswordHasher hasher, RefreshTokenStore refresh, AccessPolicy policy) {
        return new UserService(users, hasher, refresh, policy);
    }

    /** O prompt de triagem (o mesmo do AgentRH) vem de resources/prompt/resume_analysis.md e é lido uma vez, na subida. */
    @Bean
    AnalyzeResume analyzeResume(ResumeRepository resumes, VacancyRepository vacancies, UserRepository users,
            DocumentTextExtractor extractor, AiProvider ai, EmailPublisher emails) {
        return new AnalyzeResumeService(resumes, vacancies, users, extractor, ai, emails, loadPrompt());
    }

    private static String loadPrompt() {
        try (var in = new ClassPathResource("prompt/resume_analysis.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt de análise não encontrado: prompt/resume_analysis.md", e);
        }
    }
}
