package com.siot.backend.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "login_id", nullable = false, length = 20, unique = true)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String email;

    /** M-11 / NTF-09: 이메일 인증 완료 시각 (null 이면 미인증) */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false, length = 10)
    private String language = "ko";

    @Column(name = "user_type", length = 30)
    private String userType;

    @Column(name = "report_type", length = 30)
    private String reportType;

    @Column(name = "employment_status", length = 30)
    private String employmentStatus;

    @Column(name = "region_code", length = 20)
    private String regionCode;

    @Column(name = "arrival_date")
    private LocalDate arrivalDate;

    @Column(name = "move_date")
    private LocalDate moveDate;

    @Column(name = "stay_expiry")
    private LocalDate stayExpiry;

    // CHK-09 / CHK-12: 비회원 기기 값과 항목별로 비교·병합하기 위한 항목별 마지막 수정 시각
    @Column(name = "language_updated_at")
    private LocalDateTime languageUpdatedAt;

    @Column(name = "user_type_updated_at")
    private LocalDateTime userTypeUpdatedAt;

    @Column(name = "region_code_updated_at")
    private LocalDateTime regionCodeUpdatedAt;

    @Column(name = "arrival_date_updated_at")
    private LocalDateTime arrivalDateUpdatedAt;

    @Column(name = "move_date_updated_at")
    private LocalDateTime moveDateUpdatedAt;

    @Column(name = "stay_expiry_updated_at")
    private LocalDateTime stayExpiryUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** M-01: ID·비밀번호만으로 가입. 표시 이름은 ID로 시작한다. */
    public static AppUser create(String loginId, String passwordHash) {
        AppUser user = new AppUser();
        user.loginId = loginId;
        user.passwordHash = passwordHash;
        user.displayName = loginId;
        return user;
    }
}
