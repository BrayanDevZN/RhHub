package br.rhhub.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Listagens usam a projeção ResumeView, para não trazer o arquivo (bytea) junto. */
@Entity
@Table(name = "resumes")
public class ResumeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "vacancy_id", nullable = false, updatable = false)
    private Long vacancyId;

    @Column(nullable = false, updatable = false)
    private byte[] file;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ResumeEntity() {
    }

    public ResumeEntity(Long userId, Long vacancyId, byte[] file, String fileName, String contentType, String status,
            String reason, Instant createdAt) {
        this.userId = userId;
        this.vacancyId = vacancyId;
        this.file = file;
        this.fileName = fileName;
        this.contentType = contentType;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getVacancyId() { return vacancyId; }
    public byte[] getFile() { return file; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
