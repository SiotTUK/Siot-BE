package com.siot.backend.checklist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 등록한 생활 변화 (lifeChanges[] = { id, date }).
 * 같은 변화를 다시 등록하면 기준일만 바꾼다 (한 변화당 1건).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member_life_change")
@IdClass(MemberLifeChangeId.class)
public class MemberLifeChange {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** LC-01 ~ LC-08 */
    @Id
    @Column(name = "life_change_code", length = 10)
    private String lifeChangeCode;

    /** 기준일 (이사한 날, 새 직장 근무 시작일 등) */
    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static MemberLifeChange of(Long userId, String lifeChangeCode, LocalDate baseDate) {
        MemberLifeChange change = new MemberLifeChange();
        change.userId = userId;
        change.lifeChangeCode = lifeChangeCode;
        change.baseDate = baseDate;
        return change;
    }

    public void changeBaseDate(LocalDate baseDate) {
        this.baseDate = baseDate;
    }
}
