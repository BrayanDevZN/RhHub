package br.rhhub.application.web.dto;

import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.User;
import br.rhhub.domain.model.Vacancy;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** O que a API devolve (campos em snake_case, como na DOCUMENTACAO.md). Nunca inclui hash de senha nem google_sub. */
public final class Views {

    private Views() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserView(
            Long id,
            @JsonProperty("public_id") UUID publicId,
            String name,
            String email,
            String cpf,
            Integer age,
            String gender,
            @JsonProperty("two_factor") boolean twoFactor,
            String role,
            String provider,
            @JsonProperty("has_photo") Boolean hasPhoto,
            @JsonProperty("created_at") Instant createdAt) {
    }

    /** created_by só aparece para admin. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VacancyView(
            Long id,
            String name,
            String description,
            String requirements,
            String responsibilities,
            @JsonProperty("has_photo") boolean hasPhoto,
            List<String> stages,
            @JsonProperty("expires_at") Instant expiresAt,
            Integer applicants,
            @JsonProperty("created_by") Long createdBy,
            @JsonProperty("created_at") Instant createdAt) {
    }

    public record ResumeView(
            Long id,
            @JsonProperty("user_id") Long userId,
            @JsonProperty("vacancy_id") Long vacancyId,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("content_type") String contentType,
            String status,
            String reason,
            String stage,
            @JsonProperty("created_at") Instant createdAt) {
    }

    public record NotificationView(
            Long id,
            String title,
            String message,
            String link,
            boolean read,
            @JsonProperty("created_at") Instant createdAt) {
    }

    public record InboxView(List<NotificationView> items, long unread) {
    }

    public static InboxView inbox(br.rhhub.service.NotificationService.Inbox inbox) {
        return new InboxView(inbox.items().stream()
                .map(n -> new NotificationView(n.id(), n.title(), n.message(), n.link(), n.read(), n.createdAt()))
                .toList(), inbox.unread());
    }

    public static UserView user(User u, Boolean hasPhoto) {
        return new UserView(u.id(), u.publicId(), u.name(), u.email(), u.cpf(), u.age(),
                u.gender() == null ? null : u.gender().value(), u.twoFactor(), u.role().value(), u.provider().value(),
                hasPhoto, u.createdAt());
    }

    public static VacancyView vacancy(Vacancy v, boolean admin) {
        return new VacancyView(v.id(), v.name(), v.description(), v.requirements(), v.responsibilities(), v.hasPhoto(), v.stages(), v.expiresAt(), admin ? v.applicants() : null,
                admin ? v.createdBy() : null, v.createdAt());
    }

    public static ResumeView resume(Resume r) {
        return new ResumeView(r.id(), r.userId(), r.vacancyId(), r.fileName(), r.contentType(), r.status().value(),
                r.reason(), r.stage(), r.createdAt());
    }
}
