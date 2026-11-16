package br.rhhub.infra.cache;

import br.rhhub.application.port.out.ResumeRepository;
import br.rhhub.domain.model.Resume;
import br.rhhub.domain.model.ResumeFile;
import br.rhhub.domain.model.ResumeStatus;
import br.rhhub.infra.database.adapter.ResumeRepositoryAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Candidaturas NÃO são guardadas no cache: o status muda o tempo todo (a tela consulta a cada 2 s enquanto a IA
 * analisa) e o arquivo pode ter 5 MB. Este decorador só invalida o que depende delas: o contador de candidatos que
 * aparece nas vagas.
 */
@Repository
@Primary
@DependsOn("tableCreator")
@ConditionalOnProperty(name = "cache.enabled", havingValue = "true", matchIfMissing = true)
public class CachedResumeRepository implements ResumeRepository {

    private final ResumeRepository db;
    private final QueryCache cache;

    /**
     * Construtor usado pelo Spring: envolve o adapter do banco.
     */
    @Autowired
    public CachedResumeRepository(ResumeRepositoryAdapter db, QueryCache cache) {
        this((ResumeRepository) db, cache);
    }

    /** Para testes: qualquer implementação da porta serve de origem. */
    public CachedResumeRepository(ResumeRepository db, QueryCache cache) {
        this.db = db;
        this.cache = cache;
    }

    /**
     * Grava a candidatura e apaga o cache das vagas, porque o número de candidatos de cada vaga mudou.
     */
    @Override
    public Mono<Resume> create(Resume resume, byte[] file) {
        return db.create(resume, file).flatMap(saved -> cache.evictPrefix(CacheKeys.VACANCY_PREFIX).thenReturn(saved));
    }

    /**
     * Apaga a candidatura e apaga o cache das vagas (o número de candidatos mudou).
     */
    @Override
    public Mono<Void> deleteById(Long id) {
        return db.deleteById(id).then(cache.evictPrefix(CacheKeys.VACANCY_PREFIX));
    }

    /**
     * Sem cache: lê direto do banco.
     */
    @Override
    public Mono<Resume> findById(Long id) {
        return db.findById(id);
    }

    /**
     * Sem cache: lê direto do banco.
     */
    @Override
    public Flux<Resume> findByUserId(Long userId) {
        return db.findByUserId(userId);
    }

    /**
     * Sem cache: lê direto do banco.
     */
    @Override
    public Flux<Resume> findAll(ResumeStatus status, Long vacancyId) {
        return db.findAll(status, vacancyId);
    }

    /**
     * Sem cache: arquivos são grandes e lidos raramente.
     */
    @Override
    public Mono<ResumeFile> findFile(Long id) {
        return db.findFile(id);
    }

    /**
     * Sem cache: lê direto do banco.
     */
    @Override
    public Mono<Boolean> existsByUserIdAndVacancyId(Long userId, Long vacancyId) {
        return db.existsByUserIdAndVacancyId(userId, vacancyId);
    }

    /**
     * Grava o resultado direto no banco (a candidatura não é guardada em cache, então não há o que apagar).
     */
    @Override
    public Mono<Resume> updateResult(Long id, ResumeStatus status, String reason) {
        return db.updateResult(id, status, reason);
    }

    /**
     * Grava a etapa direto no banco (sem cache a invalidar).
     */
    @Override
    public Mono<Resume> updateStage(Long id, String stage) {
        return db.updateStage(id, stage);
    }
}
