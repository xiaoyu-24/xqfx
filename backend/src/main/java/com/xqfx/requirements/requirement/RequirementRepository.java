package com.xqfx.requirements.requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.LocalDateTime;
import com.xqfx.requirements.system.SystemEntity;
public interface RequirementRepository extends JpaRepository<RequirementEntity, Long>, JpaSpecificationExecutor<RequirementEntity> {
    List<RequirementEntity> findAllByDeletedFalse();
    Page<RequirementEntity> findAllByDeletedFalse(Pageable pageable);
    List<RequirementEntity> findByType_IdAndDeletedFalse(Long typeId);
    List<RequirementEntity> findBySystemIdAndDeletedFalse(Long systemId);
    List<RequirementEntity> findBySaveTypeAndDeletedFalse(RequirementSaveType saveType);
    long countBySystemIdAndDeletedFalse(Long systemId);
    long countByTargetVersionIdAndDeletedFalse(Long targetVersionId);
    java.util.Optional<RequirementEntity> findByIdAndDeletedFalse(Long id);
    long countByDeletedFalseAndSaveType(RequirementSaveType saveType);
    long countByDeletedFalseAndSaveTypeAndStatus(RequirementSaveType saveType, RequirementStatus status);

    @Modifying
    @Query("""
            update RequirementEntity requirement
               set requirement.system = :targetSystem,
                   requirement.targetVersion = null,
                   requirement.recordVersion = requirement.recordVersion + 1,
                   requirement.updatedAt = :updatedAt
             where requirement.system.id = :sourceSystemId
               and requirement.deleted = false
            """)
    int migrateSystemAndClearTargetVersion(
            @Param("sourceSystemId") Long sourceSystemId,
            @Param("targetSystem") SystemEntity targetSystem,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
