package com.siot.backend.member.repository;

import com.siot.backend.member.domain.AppUser;
import com.siot.backend.member.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** @param email 소문자로 정규화한 값 */
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    /*
     * ADM-01: 관리자 회원 검색. email 은 부분 일치 (빈 문자열이면 전체).
     * null 파라미터는 PostgreSQL 에서 타입을 알 수 없어 오류가 나므로 조건별로 메서드를 나눈다.
     */
    @Query("""
            SELECT u FROM AppUser u
            WHERE LOCATE(:email, u.email) > 0
            ORDER BY u.createdAt DESC, u.id DESC""")
    Page<AppUser> searchForAdmin(@Param("email") String email, Pageable pageable);

    @Query("""
            SELECT u FROM AppUser u
            WHERE LOCATE(:email, u.email) > 0 AND u.role = :role
            ORDER BY u.createdAt DESC, u.id DESC""")
    Page<AppUser> searchForAdmin(@Param("email") String email, @Param("role") Role role, Pageable pageable);
}
