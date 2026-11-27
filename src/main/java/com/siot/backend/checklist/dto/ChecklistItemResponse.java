package com.siot.backend.checklist.dto;

import com.siot.backend.checklist.domain.ItemType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "체크리스트 개별 항목 응답")
public record ChecklistItemResponse(
        @Schema(description = "항목 코드 (CL-01, LC-01-1 …)", example = "CL-01")
        String code,
        @Schema(description = "항목 제목", example = "외국인등록")
        String title,
        @Schema(description = "항목 구분: CHECKLIST / LIFE_CHANGE_TASK (생활수칙 RULE 은 체크리스트에 나오지 않는다)", example = "CHECKLIST")
        String kind,
        @Schema(description = "REQUIRED(필수) / INFO(안내) / OPTIONAL(선택)", example = "REQUIRED")
        ItemType itemType,
        @Schema(description = "배지 문구 (예: 입국 후 90일 이내)", example = "입국 후 90일 이내")
        String badge,
        @Schema(description = "신청 장소")
        String place,
        @Schema(description = "준비물 목록")
        List<String> docs,
        @Schema(description = "수수료")
        String fee,
        @Schema(description = "근거 법령")
        String law,
        @Schema(description = "안 하면 받는 불이익 (과태료 등)")
        String penalty,
        @Schema(description = "문의 전화")
        String contact,
        @Schema(description = "출처 링크")
        String sourceUrl,
        @Schema(description = "출처 이름")
        String sourceName,
        @Schema(description = "요약")
        String summary,
        @Schema(description = "상세 내용")
        String content,
        @Schema(description = "조건부 문구 (CL-04 등 체류 목적별 문구 및 경고)")
        List<String> variantBodies,
        @Schema(description = "마감일 (null 이면 기한 없음)", example = "2027-01-18")
        LocalDate deadline,
        @Schema(description = "D-day 일수 (음수: 지남, 0: 오늘, 양수: 남음)")
        Long dDay,
        @Schema(description = "D-day 표시 문구 (D-73, D-Day, 기한 지남 등)")
        String dDayText,
        @Schema(description = "상태: TODO / DONE / NOT_APPLICABLE / OVERDUE", example = "TODO")
        String status,
        @Schema(description = "완료 여부 (status == DONE)")
        boolean completed,
        @Schema(description = "회차 키 (1회: '', 세금: YYYY-MM, 생활변화: YYYY-MM-DD)")
        String periodKey,
        @Schema(description = "NTF-07 실제 안내 내용 변경 시각")
        OffsetDateTime noticeChangedAt,
        @Schema(description = "최근 안내 내용 변경 여부 (14일 이내)")
        boolean hasNoticeChanged,
        @Schema(description = "CAL-04 체류기간 연장 신청 시작일 (만료일 − 4개월, 그달에 없는 날짜면 말일). CL-05 외에는 null",
                nullable = true, example = "2027-07-01")
        LocalDate applyStartsOn,
        @Schema(description = "강조 표시 여부. CL-05: 체류 만료 30일 전부터 true (CAL-04)")
        boolean highlighted,

        // CHK-08 경고 표시 (외국인등록 D-30, 체류지 변경 D-7, 기한 지남 등)
        @Schema(description = "CHK-08 경고 표시 여부 (외국인등록 D-30, 체류지 변경 D-7, 기한 초과 등)", example = "true")
        boolean warning,
        @Schema(description = "CHK-08 경고 문구 (요청 언어, COM-02). 경고가 없으면 null", nullable = true,
                example = "외국인등록 기한이 30일 이내로 임박했습니다. 기한 초과 시 과태료 및 불이익이 발생합니다.")
        String warningMessage,
        @Schema(description = "CHK-08 경고 종류: OVERDUE(기한 지남) / REGISTRATION_DUE(외국인등록 D-30) / "
                + "RESIDENCE_REPORT_DUE(체류지 변경 신고 D-7). 경고가 없으면 null", nullable = true,
                allowableValues = {"OVERDUE", "REGISTRATION_DUE", "RESIDENCE_REPORT_DUE"}, example = "REGISTRATION_DUE")
        String warningType,

        // CHK-06 출처 쪽수
        @Schema(description = "CHK-06 출처 안내책자 시작 쪽수", example = "12")
        Integer pageStart,
        @Schema(description = "CHK-06 출처 안내책자 종료 쪽수", example = "14")
        Integer pageEnd,
        @Schema(description = "CHK-06 출처 쪽수 표기", example = "p.12~14")
        String sourcePages,

        @Schema(description = "항목 ID. COM-01 오류 신고(POST /api/reports)의 guideId 로 보낸다", example = "1")
        Long guideId,
        @Schema(description = "분류 코드: IMMIGRATION / HEALTH_INSURANCE / FAMILY / TAX / EDUCATION / SETTLING / CAR / EMPLOYMENT",
                nullable = true, example = "IMMIGRATION")
        String category,
        @Schema(description = "분류 표시 이름 (요청 언어)", nullable = true, example = "출입국·체류")
        String categoryLabel,

        // COM-02 본문 언어 (BE #30). 경고 문구(warningMessage)·분류 이름은 항상 요청 언어라 여기에 포함하지 않는다
        @Schema(description = "COM-02 본문(title·summary·content·badge·place·docs·fee·law·penalty·sourceName)의 실제 언어. "
                + "요청 언어 번역이 있으면 요청 언어, 없으면 한국어 원문 'ko'. 한국어 원문도 없으면 null (title 은 항목 코드)",
                nullable = true, allowableValues = {"ko", "en", "zh", "vi"}, example = "ko")
        String contentLanguage,
        @Schema(description = "COM-02 요청 언어 문구인지. false 면 한국어 원문으로 대체했거나 문구가 없음 → '번역 준비 중' 표시. "
                + "한국어 요청은 원문이 있으면 true. 조건부 문구(variantBodies)만 한국어로 대체된 경우도 false", example = "true")
        boolean translated
) {
}
