package com.xqfx.requirements.requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
interface RequirementRepository extends JpaRepository<RequirementEntity, Long> { List<RequirementEntity> findByType(RequirementType type); }
