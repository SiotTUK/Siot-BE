package com.siot.backend.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * 회원 + 온보딩 답 (체크리스트 규칙표 '온보딩 답 데이터 정의').
 * <p>
 * 로그인 ID 는 이메일이다 (11/13 결정). 답은 서버(DB)에만 저장한다 (CHK-9).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /** M-01: 로그인 ID. 항상 소문자로 저장한다 */
    @Column(nullable = false, unique = true)
    private String email;

    /** 이메일 인증 완료 시각 (null 이면 미인증 → 비밀번호 재설정·이메일 알림 대상 아님) */
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    /** ko / en / zh / vi */
    @Column(nullable = false, length = 10)
    private String language = "ko";

    // ---- 온보딩 답 ----

    /** F02 (필수). null 이면 온보딩 전 */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 10)
    private UserType userType;

    /** F03 입국 날짜 (선택) */
    @Column(name = "entry_date")
    private LocalDate entryDate;

    /** F04 체류 목적 (선택). null 이면 OTHER 로 취급 → {@link #effectivePurpose()} */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Purpose purpose;

    /** F05 거주 정보 (복수 선택) */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_residence", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "residence", nullable = false, length = 10)
    private Set<Residence> residences = new HashSet<>();

    /** F06 체류 만료일 (선택) */
    @Column(name = "stay_expiry")
    private LocalDate stayExpiry;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** M-01: 인증 코드를 확인한 이메일·비밀번호로 가입. 가입 직후 온보딩(사용자 유형)으로 이동한다. */
    public static AppUser create(String email, String passwordHash) {
        AppUser user = new AppUser();
        user.email = email;
        user.passwordHash = passwordHash;
        user.displayName = email;
        return user;
    }

    /**
     * 온보딩 답 저장 (내 정보 화면에서 바꿀 때도 사용).
     * 체크리스트는 요청마다 다시 계산하며, 완료 기록(checklist_progress)은 지우지 않는다.
     */
    public void updateOnboarding(UserType userType, LocalDate entryDate, Purpose purpose,
                                 Collection<Residence> residences, LocalDate stayExpiry) {
        this.userType = userType;
        this.entryDate = entryDate;
        this.purpose = purpose;
        this.residences.clear();
        if (residences != null && !residences.isEmpty()) {
            this.residences.addAll(EnumSet.copyOf(residences));
        }
        this.stayExpiry = stayExpiry;
    }

    /** 가입 이메일 인증 완료 */
    public void markEmailVerified(LocalDateTime verifiedAt) {
        this.emailVerifiedAt = verifiedAt;
    }

    public boolean hasVerifiedEmail() {
        return emailVerifiedAt != null;
    }

    /** M-11: 비밀번호 재설정 */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeLanguage(String language) {
        this.language = language;
    }

    public boolean isOnboarded() {
        return userType != null;
    }

    public Purpose effectivePurpose() {
        return purpose == null ? Purpose.OTHER : purpose;
    }

    public boolean hasResidence(Residence residence) {
        return residences.contains(residence);
    }
}
