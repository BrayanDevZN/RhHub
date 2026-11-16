package br.rhhub.service;

import br.rhhub.testing.UnitTest;
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

@UnitTest
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
        vacancyService = new VacancyService(vacancies, vacancies.photos, policy, resumes);
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
        assertThatThrownBy(() -> vacancyService.create(ana, "Dev", "desc", null, null).block()).isInstanceOf(DomainException.Forbidden.class);
        var vacancy = vacancyService.create(admin, "  Dev   Java ", "Backend", null, null).block();
        assertThat(vacancy.name()).isEqualTo("Dev Java");
        assertThat(vacancy.createdBy()).isEqualTo(admin.id());
        assertThatThrownBy(() -> vacancyService.create(root, "Dev Java", "outra", null, null).block()).isInstanceOf(DomainException.Conflict.class);
        assertThatThrownBy(() -> vacancyService.create(admin, " ", "d", null, null).block()).isInstanceOf(DomainException.Invalid.class);

        assertThat(vacancyService.list().collectList().block()).hasSize(1);
        assertThat(vacancyService.get(vacancy.id()).block().description()).isEqualTo("Backend");
        assertThatThrownBy(() -> vacancyService.get(99L).block()).isInstanceOf(DomainException.NotFound.class);
    }

    @Test
    void updateKeepsUntouchedFieldsAndChecksNameClash() {
        var java = vacancyService.create(admin, "Dev Java", "Backend", null, null).block();
        var qa = vacancyService.create(admin, "QA", "Testes", null, null).block();

        var edited = vacancyService.update(admin, java.id(), null, "Backend sênior", null, null).block();
        assertThat(edited.name()).isEqualTo("Dev Java");
        assertThat(edited.description()).isEqualTo("Backend sênior");
        assertThat(vacancyService.update(admin, java.id(), "Dev Java", null, null, null).block().name()).isEqualTo("Dev Java");
        assertThatThrownBy(() -> vacancyService.update(admin, qa.id(), "Dev Java", null, null, null).block()).isInstanceOf(DomainException.Conflict.class);
        assertThatThrownBy(() -> vacancyService.update(ana, qa.id(), "X", null, null, null).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> vacancyService.update(admin, 99L, "X", null, null, null).block()).isInstanceOf(DomainException.NotFound.class);

        vacancyService.delete(admin, qa.id()).block();
        assertThat(vacancies.byId).hasSize(1);
        assertThatThrownBy(() -> vacancyService.delete(ana, java.id()).block()).isInstanceOf(DomainException.Forbidden.class);
        assertThatThrownBy(() -> vacancyService.delete(admin, qa.id()).block()).isInstanceOf(DomainException.NotFound.class);
    }

    @Test
    void onlyTheDescriptionIsRequiredAndRequirementsAndResponsibilitiesAreOptional() {
        var full = vacancyService.create(admin, "Dev", "Descrição", "  Java e SQL ", "Criar APIs").block();
        assertThat(full.requirements()).isEqualTo("Java e SQL");
        assertThat(full.responsibilities()).isEqualTo("Criar APIs");

        var bare = vacancyService.create(admin, "QA", "Só a descrição", null, null).block();
        assertThat(bare.requirements()).isNull();
        var blank = vacancyService.create(admin, "Ops", "Descrição", "   ", "").block();
        assertThat(blank.requirements()).isNull();
        assertThat(blank.responsibilities()).isNull();

        assertThatThrownBy(() -> vacancyService.create(admin, "Sem desc", null, "x", "y").block()).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> vacancyService.create(admin, "Sem desc", "  ", "x", "y").block()).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> vacancyService.create(admin, "Longa", "d", "x".repeat(5001), null).block()).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> vacancyService.create(admin, "Longa", "d", null, "x".repeat(5001)).block()).isInstanceOf(DomainException.Invalid.class);
    }

    @Test
    void updateKeepsClearsOrReplacesTheOptionalFieldsButNeverEmptiesTheDescription() {
        var v = vacancyService.create(admin, "Dev", "Descrição", "Req", "Resp").block();
        assertThat(vacancyService.update(admin, v.id(), null, null, null, null).block().requirements()).isEqualTo("Req"); // null = mantém
        assertThat(vacancyService.update(admin, v.id(), null, null, "Req 2", null).block().requirements()).isEqualTo("Req 2");
        var cleared = vacancyService.update(admin, v.id(), null, null, "", "  ").block(); // vazio = apaga
        assertThat(cleared.requirements()).isNull();
        assertThat(cleared.responsibilities()).isNull();
        assertThat(cleared.description()).isEqualTo("Descrição");
        assertThatThrownBy(() -> vacancyService.update(admin, v.id(), null, " ", null, null).block()).isInstanceOf(DomainException.Invalid.class);
    }

    @Test
    void vacancyPhotoIsManagedByAdminsAndSeenByEveryone() {
        var v = vacancyService.create(admin, "Dev", "Descrição", null, null).block();
        assertThat(vacancyService.get(v.id()).block().hasPhoto()).isFalse();
        assertThatThrownBy(() -> vacancyService.photo(v.id()).block()).isInstanceOf(DomainException.NotFound.class);

        assertThatThrownBy(() -> vacancyService.uploadPhoto(ana, v.id(), JPEG).block()).isInstanceOf(DomainException.Forbidden.class);
        vacancyService.uploadPhoto(admin, v.id(), JPEG).block();
        assertThat(vacancyService.get(v.id()).block().hasPhoto()).isTrue();
        assertThat(vacancyService.list().collectList().block()).allMatch(br.rhhub.domain.model.Vacancy::hasPhoto);
        assertThat(vacancyService.photo(v.id()).block().contentType()).isEqualTo("image/jpeg");

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
        vacancyService.uploadPhoto(admin, v.id(), png).block(); // troca
        assertThat(vacancyService.photo(v.id()).block().contentType()).isEqualTo("image/png");

        // por conteúdo: SVG, GIF, vazio e grande demais são recusados, e a foto anterior fica
        assertThatThrownBy(() -> vacancyService.uploadPhoto(admin, v.id(), "<svg onload=alert(1)>".getBytes()).block()).isInstanceOf(DomainException.Invalid.class);
        assertThatThrownBy(() -> vacancyService.uploadPhoto(admin, v.id(), new byte[0]).block()).isInstanceOf(DomainException.Invalid.class);
        assertThat(vacancyService.photo(v.id()).block().contentType()).isEqualTo("image/png");
        assertThatThrownBy(() -> vacancyService.uploadPhoto(admin, 999L, JPEG).block()).isInstanceOf(DomainException.NotFound.class);

        assertThatThrownBy(() -> vacancyService.removePhoto(ana, v.id()).block()).isInstanceOf(DomainException.Forbidden.class);
        vacancyService.removePhoto(admin, v.id()).block();
        assertThat(vacancyService.get(v.id()).block().hasPhoto()).isFalse();
        // excluir a vaga leva a foto junto
        vacancyService.uploadPhoto(admin, v.id(), JPEG).block();
        vacancyService.delete(admin, v.id()).block();
        assertThat(vacancies.photos.byVacancy).isEmpty();
    }

    // ---- candidaturas ----

    private Long vacancyId() {
        return vacancyService.create(admin, "Dev", "Backend", null, null).block().id();
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
    void ifQueueingFailsTheUploadStillSucceedsAndTheResumeStaysPendingForTheRepublisher() {
        Long vacancy = vacancyId();
        analysis.failing = true;
        var saved = resumeService.submit(ana, vacancy, "a.pdf", PDF).block(); // não falha nem espera o Kafka
        assertThat(saved.status()).isEqualTo(ResumeStatus.PENDING);
        assertThat(resumes.byId).containsKey(saved.id());
        assertThat(analysis.queued).isEmpty();
    }

    @Test
    void theUploadDoesNotWaitForASlowQueue() {
        Long vacancy = vacancyId();
        var never = new java.util.concurrent.atomic.AtomicBoolean();
        var slow = new br.rhhub.application.port.out.ResumeAnalysisPublisher() {
            public reactor.core.publisher.Mono<Void> publish(Long id) {
                never.set(true);
                return reactor.core.publisher.Mono.never(); // um Kafka que nunca responde
            }
        };
        var service = new ResumeService(resumes, vacancies, new PdfDocxTextExtractor(), slow);
        var saved = service.submit(ana, vacancy, "a.pdf", PDF).block(java.time.Duration.ofSeconds(2));
        assertThat(saved).isNotNull();
        assertThat(never).isTrue(); // o aviso foi disparado, mas a resposta não ficou presa nele
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
