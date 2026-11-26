package com.siot.backend.checklist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** CHK-06 항목 상세의 언어별 문구 (ko / en / zh / vi) */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "guide_translation")
@IdClass(GuideTranslationId.class)
public class GuideTranslation {

    @Id
    @Column(length = 10)
    private String language;

    @Id
    @Column(name = "guide_id")
    private Long guideId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(columnDefinition = "text")
    private String content;

    /** 예: "입국 후 90일 이내" */
    @Column(length = 100)
    private String badge;

    /** 신청 장소 */
    @Column(columnDefinition = "text")
    private String place;

    /** 준비물 */
    @Column(columnDefinition = "text[]")
    private List<String> docs = new ArrayList<>();

    private String fee;

    /** 근거 법령 */
    private String law;

    /** 안 하면 (과태료 등) */
    @Column(columnDefinition = "text")
    private String penalty;

    /** 출처 이름 (링크는 Guide.sourceUrl) */
    @Column(name = "source_name")
    private String sourceName;

    /** #14 확인된 문장 목록 (생활수칙 본문, 순서 유지) */
    @Column(columnDefinition = "text[]", nullable = false)
    private List<String> points = new ArrayList<>();

    /** #14 시기 (FE facts.when) */
    @Column(columnDefinition = "text")
    private String timing;

    /** #14 언어별 문의처. 없으면 Guide.contact */
    @Column(columnDefinition = "text")
    private String contact;

    /** #14 사람이 확인한 번역. true 면 자동 번역({@code GuideTranslationGenerator})이 덮어쓰지 않는다 */
    @Column(nullable = false)
    private boolean reviewed = false;

    /** #14 생활수칙 칸: 문장 목록·시기·언어별 문의처 (null 이면 지움) */
    public void updateRuleFields(List<String> points, String timing, String contact) {
        this.points = points == null ? new ArrayList<>() : new ArrayList<>(points);
        this.timing = timing;
        this.contact = contact;
    }

    public void markReviewed(boolean reviewed) {
        this.reviewed = reviewed;
    }

    public static GuideTranslation create(Long guideId, String language) {
        GuideTranslation translation = new GuideTranslation();
        translation.guideId = guideId;
        translation.language = language;
        return translation;
    }

    public static GuideTranslation create(Long guideId, String language, String title, String summary,
                                          String content, String badge, String place, List<String> docs,
                                          String fee, String law, String penalty, String sourceName) {
        GuideTranslation translation = new GuideTranslation();
        translation.guideId = guideId;
        translation.language = language;
        translation.update(title, summary, content, badge, place, docs, fee, law, penalty, sourceName);
        return translation;
    }

    /** 관리자 저장: 보낸 값으로 모두 바꾼다 (빈 값은 지움) */
    public void update(String title, String summary, String content, String badge, String place,
                       List<String> docs, String fee, String law, String penalty, String sourceName) {
        this.title = title;
        this.summary = summary;
        this.content = content;
        this.badge = badge;
        this.place = place;
        this.docs = docs == null ? new ArrayList<>() : new ArrayList<>(docs);
        this.fee = fee;
        this.law = law;
        this.penalty = penalty;
        this.sourceName = sourceName;
    }
}
