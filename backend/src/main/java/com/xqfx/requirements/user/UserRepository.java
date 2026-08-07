package com.xqfx.requirements.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    List<UserEntity> findAllByOrderByDisplayNameAsc();

    List<UserEntity> findByDisabledFalseOrderByDisplayNameAsc();
}
