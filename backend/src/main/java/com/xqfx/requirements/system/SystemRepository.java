package com.xqfx.requirements.system;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SystemRepository extends JpaRepository<SystemEntity, Long> {
    List<SystemEntity> findAllByDeletedFalse();
    Optional<SystemEntity> findByIdAndDeletedFalse(Long id);
    boolean existsByActiveNameKey(String activeNameKey);
    boolean existsByActiveNameKeyAndIdNot(String activeNameKey, Long id);
}
