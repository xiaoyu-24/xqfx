package com.xqfx.requirements.requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Collection;
public interface RequirementRepository extends JpaRepository<RequirementEntity, Long>, JpaSpecificationExecutor<RequirementEntity> {
    List<RequirementEntity> findAllByDeletedFalse();
    Page<RequirementEntity> findAllByDeletedFalse(Pageable pageable);
    List<RequirementEntity> findByType_IdAndDeletedFalse(Long typeId);
    List<RequirementEntity> findBySystemIdAndDeletedFalse(Long systemId);
    List<RequirementEntity> findBySaveTypeAndDeletedFalse(RequirementSaveType saveType);
    long countBySystemIdAndDeletedFalse(Long systemId);
    long countByTargetVersionIdAndDeletedFalse(Long targetVersionId);
    java.util.Optional<RequirementEntity> findByIdAndDeletedFalse(Long id);
    long countByDeletedFalse();
    long countByDeletedFalseAndSaveType(RequirementSaveType saveType);
    long countByDeletedFalseAndUrgency(RequirementUrgency urgency);
    long countByDeletedFalseAndSaveTypeAndStatus(RequirementSaveType saveType, RequirementStatus status);
    long countByDeletedFalseAndSaveTypeAndUrgency(RequirementSaveType saveType, RequirementUrgency urgency);
    long countByDeletedFalseAndSaveTypeAndStatusNotIn(RequirementSaveType saveType, Collection<RequirementStatus> statuses);
    long countByDeletedFalseAndSaveTypeAndUrgencyAndStatusNotIn(
            RequirementSaveType saveType,
            RequirementUrgency urgency,
            Collection<RequirementStatus> statuses);

    @Query("""
            select system.id as systemId,
                   coalesce(system.name, '未关联系统') as systemName,
                   count(requirement) as requirementCount
            from RequirementEntity requirement
            left join requirement.system system
            where requirement.deleted = false
            group by system.id, system.name
            order by count(requirement) desc, system.name asc
            """)
    List<SystemRequirementCount> countRequirementsBySystem();

    interface SystemRequirementCount {
        Long getSystemId();
        String getSystemName();
        Long getRequirementCount();
    }

    @Query("""
            select requirement
            from RequirementEntity requirement
            join requirement.system system
            where system.ownerUser.id = :userId
              and requirement.deleted = false
              and requirement.saveType = :saveType
              and requirement.status not in :terminalStatuses
            order by requirement.updatedAt desc
            """)
    List<RequirementEntity> findWorkbenchOwned(
            @Param("userId") Long userId,
            @Param("saveType") RequirementSaveType saveType,
            @Param("terminalStatuses") Collection<RequirementStatus> terminalStatuses);

    @Query("""
            select distinct requirement
            from RequirementEntity requirement
            join requirement.system system
            join system.collaboratorUsers collaborator
            where collaborator.id = :userId
              and requirement.deleted = false
              and requirement.saveType = :saveType
              and requirement.status not in :terminalStatuses
            order by requirement.updatedAt desc
            """)
    List<RequirementEntity> findWorkbenchAssisting(
            @Param("userId") Long userId,
            @Param("saveType") RequirementSaveType saveType,
            @Param("terminalStatuses") Collection<RequirementStatus> terminalStatuses);

}
