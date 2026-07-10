package com.xqfx.requirements.requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface RequirementRepository extends JpaRepository<RequirementEntity, Long>, JpaSpecificationExecutor<RequirementEntity> { List<RequirementEntity> findAllByDeletedFalse(); Page<RequirementEntity> findAllByDeletedFalse(Pageable pageable); List<RequirementEntity> findByTypeAndDeletedFalse(RequirementType type); List<RequirementEntity> findBySystemIdAndDeletedFalse(Long systemId); List<RequirementEntity> findBySaveTypeAndDeletedFalse(RequirementSaveType saveType); long countBySystemIdAndDeletedFalse(Long systemId); long countByTargetVersionIdAndDeletedFalse(Long targetVersionId); java.util.Optional<RequirementEntity> findByIdAndDeletedFalse(Long id); }
