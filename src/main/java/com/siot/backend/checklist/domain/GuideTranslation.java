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
}
