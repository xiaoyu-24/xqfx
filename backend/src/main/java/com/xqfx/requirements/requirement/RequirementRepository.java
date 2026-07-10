package com.xqfx.requirements.requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
interface RequirementRepository extends JpaRepository<RequirementEntity, Long> { List<RequirementEntity> findAllByDeletedFalse(); List<RequirementEntity> findByTypeAndDeletedFalse(RequirementType type); List<RequirementEntity> findBySystemIdAndDeletedFalse(Long systemId); List<RequirementEntity> findBySaveTypeAndDeletedFalse(RequirementSaveType saveType); java.util.Optional<RequirementEntity> findByIdAndDeletedFalse(Long id); }
