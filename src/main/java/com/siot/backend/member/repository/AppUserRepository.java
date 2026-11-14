package com.siot.backend.member.repository;

import com.siot.backend.member.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** @param email 소문자로 정규화한 값 */
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
