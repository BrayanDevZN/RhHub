package br.rhhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.auth.AccessPolicy;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.AuthProvider;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.domain.model.Role;
import br.rhhub.domain.model.User;
import br.rhhub.infra.document.PdfDocxTextExtractor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManagementServicesTest {

    private static final byte[] PDF = "%PDF-1.7 conteudo".getBytes(StandardCharsets.UTF_8);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2};

    private Fakes.Users users;
    private Fakes.Vacancies vacancies;
    private Fakes.Resumes resumes;
    private Fakes.Analysis analysis;
    private Fakes.Photos photos;
    private Fakes.Refresh refresh;
    private AccessPolicy policy;
    private VacancyService vacancyService;
    private ResumeService resumeService;
    private AdminService adminService;
    private ProfilePhotoService photoService;
    private User root;
    private User admin;
    private User ana;
    private User bia;

    private User add(String email, Role role) {
        return users.save(new User(null, UUID.randomUUID(), email.split("@")[0], email, null, 30, null, "hash", false, role,
                AuthProvider.LOCAL, null, null)).block();
    }

    @BeforeEach
    void setUp() {
        users = new Fakes.Users();
        vacancies = new Fakes.Vacancies();
        resumes = new Fakes.Resumes();
        analysis = new Fakes.Analysis();
        photos = new Fakes.Photos();
        refresh = new Fakes.Refresh();
        policy = new AccessPolicy("root@gmail.com");
        vacancyService = new VacancyService(vacancies, policy);
        resumeService = new ResumeService(resumes, vacancies, new PdfDocxTextExtractor(), analysis);
        adminService = new AdminService(users, refresh, policy);
        photoService = new ProfilePhotoService(photos, users);
        root = add("root@gmail.com", Role.ADMIN);
        admin = add("adm@gmail.com", Role.ADMIN);
        ana = add("ana@gmail.com", Role.USER);
        bia = add("bia@gmail.com", Role.USER);
    }

    // ---- vagas ----

    @Test
    void onlyAdminsManageVacanciesAndNamesAreUnique() {
        assertThatThrownBy(() -> vacancyService.create(ana, "Dev", "desc").block()).isInstanceOf(DomainException.Forbidden.class);
        var vacancy = vacancyService.create(admin, "  Dev   Java ", "Backend").block();
        assertThat(vacancy.name()).isEqualTo("Dev Java");
        assertThat(vacancy.createdBy()).isEqualTo(admin.id());
        assertThatThrownBy(() -> vacancyService.create(root, "Dev Java", "outra").block()).isInstanceOf(DomainException.Conflict.class);
        assertThatThrownBy(() -> vacancyService.create(admin, " ", "d").block()).isInstanceOf(DomainException.Invalid.class);

        assertThat(vacancyService.list().collectList().block()).hasSize(1);
        assertThat(vacancyService.get(vacancy.id()).block().description()).isEqualTo("Backend");
        assertThatThrownBy(() -> vacancyService.get(99L).block()).isInstanceOf(DomainException.NotFound.class);
    }

    @Test
    void updateKeepsUntouchedFieldsAndChecksNameClash() {
        var java = vacancyService.create(admin, "Dev Java", "Backend").block();
        var qa = vacancyService.create(admin, "QA", "Testes").block();

        var edited = vacancyService.update(admin, java.id(), null, "Backend sênior").block();
        assertThat(edited.name()).isEqualTo("Dev Java");
        assertThat(edited.description()).isEqualTo("Backend sênior");
        assertThat(vacancyService.update(admin, java.id(), "Dev Java", null).block().name()).isEqualTo("Dev Java");
        assertThatThrownBy(() -> vacancyService.update(admin, qa.id(), "Dev Java", null).block()).isInstanceOf(DomainException.Conflict.class);
        assertThatThrownBy(() -> vacancyService.update(ana, qa.id(), "X", null).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> vacancyService.update(admin, 99L, "X", null).block()).isInstanceOf(DomainException.NotFound.class);

        vacancyService.delete(admin, qa.id()).block();
        assertThat(vacancies.byId).hasSize(1);
        assertThatThrownBy(() -> vacancyService.delete(ana, java.id()).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> vacancyService.delete(admin, qa.id()).block()).isInstanceOf(DomainException.NotFound.class);
    }

    // ---- candidaturas ----

    private Long vacancyId() {
        return vacancyService.create(admin, "Dev", "Backend").block().id();
    }

    @Test
    void submitStoresAsPendingAndQueuesTheAnalysis() {
        Long vacancy = vacancyId();
        var resume = resumeService.submit(ana, vacancy, "C:\\docs\\meu cv.pdf", PDF).block();
        assertThat(resume.status()).isEqualTo(ResumeStatus.PENDING);
        assertThat(resume.fileName()).isEqualTo("meu cv.pdf");
        assertThat(resume.contentType()).isEqualTo("application/pdf");
        assertThat(analysis.queued).containsExactly(resume.id());
        assertThat(resumes.files.get(resume.id())).isEqualTo(PDF);
    }

    @Test
    void submitRefusesBadFilesMissingVacancyAndDuplicates() {
        Long vacancy = vacancyId();
        assertThatThrownBy(() -> resumeService.submit(ana, vacancy, "a.pdf", "texto puro".getBytes()).block())
                .isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> resumeService.submit(ana, vacancy, "a.pdf", new byte[0]).block()).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> resumeService.submit(ana, vacancy, "a.pdf", new byte[ResumeService.MAX_BYTES + 1]).block())
                .isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> resumeService.submit(ana, null, "a.pdf", PDF).block()).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> resumeService.submit(ana, 999L, "a.pdf", PDF).block()).isInstanceOf(DomainException.NotFound.class);

        resumeService.submit(ana, vacancy, "a.pdf", PDF).block();
        assertThatThrownBy(() -> resumeService.submit(ana, vacancy, "b.pdf", PDF).block()).isInstanceOf(DomainException.Conflict.class);
        resumeService.submit(bia, vacancy, "b.pdf", PDF).block(); // outro candidato pode
        assertThat(analysis.queued).hasSize(2);
    }

    @Test
    void ifQueueingFailsTheResumeIsRolledBackSoTheUserCanRetry() {
        Long vacancy = vacancyId();
        analysis.failing = true;
        assertThatThrownBy(() -> resumeService.submit(ana, vacancy, "a.pdf", PDF).block()).hasMessageContaining("kafka");
        assertThat(resumes.byId).isEmpty();
        analysis.failing = false;
        assertThat(resumeService.submit(ana, vacancy, "a.pdf", PDF).block().id()).isNotNull();
    }

    @Test
    void ownerAndAdminsReadAndDeleteButOthersCannot() {
        Long vacancy = vacancyId();
        var mine = resumeService.submit(ana, vacancy, "a.pdf", PDF).block();

        assertThat(resumeService.listMine(ana).collectList().block()).hasSize(1);
        assertThat(resumeService.listMine(bia).collectList().block()).isEmpty();
        assertThat(resumeService.get(ana, mine.id()).block().id()).isEqualTo(mine.id());
        assertThat(resumeService.get(admin, mine.id()).block().id()).isEqualTo(mine.id());
        assertThat(resumeService.file(ana, mine.id()).block().content()).isEqualTo(PDF);
        assertThatThrownBy(() -> resumeService.get(bia, mine.id()).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> resumeService.file(bia, mine.id()).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> resumeService.delete(bia, mine.id()).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> resumeService.get(ana, 404L).block()).isInstanceOf(DomainException.NotFound.class);

        assertThatThrownBy(() -> resumeService.listAll(ana, null, null).collectList().block()).isInstanceOf(DomainException.Forbidden.class);
        assertThat(resumeService.listAll(admin, ResumeStatus.PENDING, vacancy).collectList().block()).hasSize(1);
        assertThat(resumeService.listAll(admin, ResumeStatus.APROVED, null).collectList().block()).isEmpty();

        resumeService.delete(ana, mine.id()).block();
        assertThat(resumes.byId).isEmpty();
        // com a candidatura apagada, dá para se candidatar de novo
        assertThat(resumeService.submit(ana, vacancy, "a.pdf", PDF).block()).isNotNull();
    }

    @Test
    void fileNameIsReducedToASafeName() {
        assertThat(ResumeService.cleanName("../../etc/passwd", "pdf")).isEqualTo("passwd");
        assertThat(ResumeService.cleanName(null, "docx")).isEqualTo("curriculo.docx");
        assertThat(ResumeService.cleanName("a\u0000b\n.pdf", "pdf")).isEqualTo("ab.pdf");
        assertThat(ResumeService.cleanName("x".repeat(400), "pdf")).hasSize(255);
    }

    // ---- administração ----

    @Test
    void adminPromotesButOnlyRootDemotes() {
        assertThatThrownBy(() -> adminService.listUsers(ana).collectList().block()).isInstanceOf(DomainException.Forbidden.class);
        assertThat(adminService.listUsers(admin).collectList().block()).hasSize(4);
        assertThat(adminService.getUser(admin, ana.publicId()).block().email()).isEqualTo("ana@gmail.com");
        assertThatThrownBy(() -> adminService.getUser(admin, UUID.randomUUID()).block()).isInstanceOf(DomainException.NotFound.class);

        var promoted = adminService.setRole(admin, ana.publicId(), "admin").block();
        assertThat(promoted.role()).isEqualTo(Role.ADMIN);
        assertThat(adminService.setRole(admin, ana.publicId(), "admin").block().role()).isEqualTo(Role.ADMIN); // sem mudança

        assertThatThrownBy(() -> adminService.setRole(admin, ana.publicId(), "user").block()).isInstanceOf(DomainException.Forbidden.class);
        assertThat(adminService.setRole(root, ana.publicId(), "user").block().role()).isEqualTo(Role.USER);
        assertThatThrownBy(() -> adminService.setRole(root, root.publicId(), "user").block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> adminService.setRole(root, ana.publicId(), "chefe").block()).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> adminService.setRole(ana, bia.publicId(), "admin").block()).isInstanceOf(DomainException.Forbidden.class);
    }

    @Test
    void onlyRootDeletesOtherAccountsAndRootIsProtected() {
        refresh.save(ana.publicId(), "t").block();
        assertThatThrownBy(() -> adminService.deleteUser(admin, ana.publicId()).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> adminService.deleteUser(ana, bia.publicId()).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> adminService.deleteUser(admin, root.publicId()).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> adminService.deleteUser(root, root.publicId()).block()).isInstanceOf(DomainException.Forbidden.class);

        adminService.deleteUser(root, ana.publicId()).block();
        assertThat(users.findByEmail("ana@gmail.com").block()).isNull();
        assertThat(refresh.tokens).doesNotContainKey(ana.publicId());
        assertThatThrownBy(() -> adminService.deleteUser(root, ana.publicId()).block()).isInstanceOf(DomainException.NotFound.class);
    }

    // ---- foto de perfil ----

    @Test
    void userUploadsReplacesAndRemovesTheProfilePhoto() {
        assertThat(photoService.has(ana).block()).isFalse();
        assertThatThrownBy(() -> photoService.get(ana, null).block()).isInstanceOf(DomainException.NotFound.class);

        photoService.upload(ana, JPEG).block();
        assertThat(photoService.has(ana).block()).isTrue();
        var photo = photoService.get(ana, null).block();
        assertThat(photo.contentType()).isEqualTo("image/jpeg");
        assertThat(photo.content()).isEqualTo(JPEG);
        assertThat(photoService.get(ana, ana.publicId()).block().content()).isEqualTo(JPEG);

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
        photoService.upload(ana, png).block();
        assertThat(photoService.get(ana, null).block().contentType()).isEqualTo("image/png");
        byte[] webp = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 9};
        photoService.upload(ana, webp).block();
        assertThat(photoService.get(ana, null).block().contentType()).isEqualTo("image/webp");

        photoService.remove(ana).block();
        assertThat(photoService.has(ana).block()).isFalse();
    }

    @Test
    void photoIsCheckedByContentNotByWhatTheClientSays() {
        assertThatThrownBy(() -> photoService.upload(ana, "GIF89a....".getBytes()).block()).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> photoService.upload(ana, "<svg onload=alert(1)>".getBytes()).block()).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> photoService.upload(ana, new byte[0]).block()).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> photoService.upload(ana, null).block()).isInstanceOf(DomainException.Invalid.class);
        byte[] big = new byte[br.rhhub.domain.model.ProfilePhoto.MAX_BYTES + 1];
        big[0] = (byte) 0xFF; big[1] = (byte) 0xD8; big[2] = (byte) 0xFF;
        assertThatThrownBy(() -> photoService.upload(ana, big).block()).isInstanceOf(DomainException.Invalid.class);
        assertThat(photos.byUser).isEmpty();
    }

    @Test
    void onlyAdminsSeeSomeoneElsesPhoto() {
        photoService.upload(ana, JPEG).block();
        assertThatThrownBy(() -> photoService.get(bia, ana.publicId()).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThat(photoService.get(admin, ana.publicId()).block().content()).isEqualTo(JPEG);
        assertThatThrownBy(() -> photoService.get(admin, UUID.randomUUID()).block()).isInstanceOf(DomainException.NotFound.class);
        assertThatThrownBy(() -> photoService.get(admin, bia.publicId()).block()).isInstanceOf(DomainException.NotFound.class);
    }
}
