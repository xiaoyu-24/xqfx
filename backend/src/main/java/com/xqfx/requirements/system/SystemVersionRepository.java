package com.xqfx.requirements.system;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SystemVersionRepository extends JpaRepository<SystemVersionEntity, Long> {

    List<SystemVersionEntity> findBySystemIdOrderByNameAsc(Long systemId);
}
