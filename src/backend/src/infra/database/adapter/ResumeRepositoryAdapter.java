package br.rhhub.infra.database.adapter;

import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeFile;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.infra.database.Params;
import br.rhhub.infra.database.Row;
import br.rhhub.infra.database.SqlExecutor;
import java.time.Clock;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** As consultas de metadados não selecionam a coluna file, para não trazer o PDF junto. */
@Repository
@DependsOn("tableCreator")
public class ResumeRepositoryAdapter implements ResumeRepository {

    private static final String COLUMNS = "id, user_id, vacancy_id, file_name, content_type, status, reason, stage, created_at";

    private final SqlExecutor sql;
    private final Clock clock;

    /**
     * Cria o adapter com o executor de SQL e o relógio.
     */
    public ResumeRepositoryAdapter(SqlExecutor sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    /**
     * Busca a candidatura pelo id, sem o arquivo (que é pesado e tem consulta própria).
     *
     * @return a candidatura, ou vazio.
     */
    @Override
    public Mono<Resume> findById(Long id) {
        return sql.query("SELECT " + COLUMNS + " FROM resumes WHERE id = :id", Params.of().add("id", id)).next()
                .map(ResumeRepositoryAdapter::toDomain);
    }

    /**
     * Lista as candidaturas de um usuário, as mais novas primeiro.
     */
    @Override
    public Flux<Resume> findByUserId(Long userId) {
        return sql.query("SELECT " + COLUMNS + " FROM resumes WHERE user_id = :user_id ORDER BY created_at DESC, id DESC",
                Params.of().add("user_id", userId)).map(ResumeRepositoryAdapter::toDomain);
    }

    /**
     * Lista candidaturas com filtros opcionais, as mais novas primeiro. O SQL é montado só com condições fixas; os valores sempre vão como parâmetros.
     *
     * @param status filtra por situação, ou nulo para todas.
     * @param vacancyId filtra por vaga, ou nulo para todas.
     */
    @Override
    public Flux<Resume> findAll(ResumeStatus status, Long vacancyId) {
        var query = new StringBuilder("SELECT " + COLUMNS + " FROM resumes WHERE 1 = 1");
        var params = Params.of();
        if (status != null) {
            query.append(" AND status = :status");
            params.add("status", status.value());
        }
        if (vacancyId != null) {
            query.append(" AND vacancy_id = :vacancy_id");
            params.add("vacancy_id", vacancyId);
        }
        query.append(" ORDER BY created_at DESC, id DESC");
        return sql.query(query.toString(), params).map(ResumeRepositoryAdapter::toDomain);
    }

    /**
     * Busca só o arquivo do currículo (bytes, nome e tipo). Separado de findById para listagens não carregarem arquivos.
     */
    @Override
    public Mono<ResumeFile> findFile(Long id) {
        return sql.query("SELECT file, file_name, content_type FROM resumes WHERE id = :id", Params.of().add("id", id))
                .next()
                .map(r -> new ResumeFile(r.bytes("file"), r.string("file_name"), r.string("content_type")));
    }

    /**
     * Diz se o usuário já se candidatou àquela vaga.
     */
    @Override
    public Mono<Boolean> existsByUserIdAndVacancyId(Long userId, Long vacancyId) {
        return sql.query("SELECT 1 AS found FROM resumes WHERE user_id = :user_id AND vacancy_id = :vacancy_id LIMIT 1",
                Params.of().add("user_id", userId).add("vacancy_id", vacancyId)).hasElements();
    }

    /**
     * Grava a candidatura nova junto com o arquivo e devolve a linha criada.
     *
     * @throws DomainException.Conflict se o usuário já se candidatou à vaga (restrição única usuário + vaga).
     * @throws DomainException.NotFound se o usuário ou a vaga não existem.
     */
    @Override
    public Mono<Resume> create(Resume resume, byte[] file) {
        var params = Params.of()
                .add("user_id", resume.userId())
                .add("vacancy_id", resume.vacancyId())
                .add("file", file)
                .add("file_name", resume.fileName())
                .add("content_type", resume.contentType())
                .add("status", resume.status().value())
                .add("reason", resume.reason(), String.class)
                .add("stage", resume.stage())
                .add("created_at", resume.createdAt() != null ? resume.createdAt() : clock.instant());
        return sql.query("""
                INSERT INTO resumes (user_id, vacancy_id, file, file_name, content_type, status, reason, stage, created_at)
                VALUES (:user_id, :vacancy_id, :file, :file_name, :content_type, :status, :reason, :stage, :created_at)
                RETURNING\s""" + COLUMNS, params)
                .next()
                .map(ResumeRepositoryAdapter::toDomain)
                .onErrorMap(SqlExecutor.ConstraintViolation.class, ResumeRepositoryAdapter::translate);
    }

    /**
     * Atualiza só a situação e o motivo (resultado da análise da IA).
     *
     * @throws DomainException.NotFound se a candidatura não existe.
     */
    @Override
    public Mono<Resume> updateResult(Long id, ResumeStatus status, String reason) {
        return sql.query("UPDATE resumes SET status = :status, reason = :reason WHERE id = :id RETURNING " + COLUMNS,
                Params.of().add("id", id).add("status", status.value()).add("reason", reason, String.class))
                .next()
                .map(ResumeRepositoryAdapter::toDomain)
                .switchIfEmpty(Mono.error(new DomainException.NotFound("Candidatura não encontrada")));
    }

    /**
     * Muda só a etapa do processo seletivo.
     *
     * @throws DomainException.NotFound se a candidatura não existe.
     */
    @Override
    public Mono<Resume> updateStage(Long id, String stage) {
        return sql.query("UPDATE resumes SET stage = :stage WHERE id = :id RETURNING " + COLUMNS,
                Params.of().add("id", id).add("stage", stage))
                .next()
                .map(ResumeRepositoryAdapter::toDomain)
                .switchIfEmpty(Mono.error(new DomainException.NotFound("Candidatura não encontrada")));
    }

    /**
     * Apaga a candidatura (e o arquivo dela).
     */
    @Override
    public Mono<Void> deleteById(Long id) {
        return sql.update("DELETE FROM resumes WHERE id = :id", Params.of().add("id", id)).then();
    }

    /**
     * Traduz a violação de restrição ao gravar: chave estrangeira vira 404 (usuário ou vaga não existem); qualquer outra, a de candidatura duplicada, vira 409.
     */
    private static Throwable translate(SqlExecutor.ConstraintViolation e) {
        String detail = e.detail();
        if (detail.contains("FOREIGN KEY") || detail.contains("foreign key")) {
            return new DomainException.NotFound("Usuário ou vaga não encontrados");
        }
        return new DomainException.Conflict("Você já se candidatou a esta vaga", e);
    }

    /**
     * Converte uma linha da tabela resumes no objeto {@link Resume} (sem o arquivo).
     */
    static Resume toDomain(Row r) {
        return new Resume(r.longValue("id"), r.longValue("user_id"), r.longValue("vacancy_id"), r.string("file_name"),
                r.string("content_type"), ResumeStatus.from(r.string("status")), r.string("reason"), r.instant("created_at"),
                r.string("stage"));
    }
}
