package com.xqfx.requirements.system;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemVersionRepository extends JpaRepository<SystemVersionEntity, Long> {

    List<SystemVersionEntity> findBySystemIdAndDeletedFalseOrderByNameAsc(Long systemId);

    long countBySystemIdAndDeletedFalse(Long systemId);
}
