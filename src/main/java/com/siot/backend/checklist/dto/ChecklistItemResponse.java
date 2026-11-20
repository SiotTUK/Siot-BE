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
        @Schema(description = "항목 구분: CHECKLIST / LIFE_CHANGE_TASK / RULE", example = "CHECKLIST")
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
        boolean hasNoticeChanged
) {
}
