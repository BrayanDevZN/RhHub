package br.rhhub.infrastructure.persistence.jpa;

import br.rhhub.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByPublicId(UUID publicId);

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByGoogleSub(String googleSub);

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    @Modifying
    @Query("delete from UserEntity u where u.publicId = :publicId")
    int deleteByPublicId(@Param("publicId") UUID publicId);
}
