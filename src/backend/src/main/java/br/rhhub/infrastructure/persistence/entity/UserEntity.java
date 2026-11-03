package br.rhhub.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String cpf;

    private Integer age;

    private String gender;

    @Column(name = "password")
    private String passwordHash;

    @Column(name = "two_factor", nullable = false)
    private boolean twoFactor;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String provider;

    @Column(name = "google_sub")
    private String googleSub;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserEntity() {
    }

    public UserEntity(Long id, UUID publicId, String name, String email, String cpf, Integer age, String gender,
            String passwordHash, boolean twoFactor, String role, String provider, String googleSub, Instant createdAt) {
        this.id = id;
        this.publicId = publicId;
        this.name = name;
        this.email = email;
        this.cpf = cpf;
        this.age = age;
        this.gender = gender;
        this.passwordHash = passwordHash;
        this.twoFactor = twoFactor;
        this.role = role;
        this.provider = provider;
        this.googleSub = googleSub;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getCpf() { return cpf; }
    public Integer getAge() { return age; }
    public String getGender() { return gender; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isTwoFactor() { return twoFactor; }
    public String getRole() { return role; }
    public String getProvider() { return provider; }
    public String getGoogleSub() { return googleSub; }
    public Instant getCreatedAt() { return createdAt; }
}
