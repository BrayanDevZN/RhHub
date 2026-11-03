package br.rhhub.infra.database.jpa;

import br.rhhub.infra.database.entity.ResumeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeJpaRepository extends JpaRepository<ResumeEntity, Long> {

    Optional<ResumeView> findViewById(Long id);

    List<ResumeView> findViewsByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            select r.id as id, r.userId as userId, r.vacancyId as vacancyId, r.fileName as fileName,
                   r.contentType as contentType, r.status as status, r.reason as reason, r.createdAt as createdAt
            from ResumeEntity r
            where (:status is null or r.status = :status)
              and (:vacancyId is null or r.vacancyId = :vacancyId)
            order by r.createdAt desc
            """)
    List<ResumeView> search(@Param("status") String status, @Param("vacancyId") Long vacancyId);

    Optional<ResumeFileView> findFileViewById(Long id);

    boolean existsByUserIdAndVacancyId(Long userId, Long vacancyId);

    @Modifying
    @Query("update ResumeEntity r set r.status = :status, r.reason = :reason where r.id = :id")
    int updateResult(@Param("id") Long id, @Param("status") String status, @Param("reason") String reason);
}
