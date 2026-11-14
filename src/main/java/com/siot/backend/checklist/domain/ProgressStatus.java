package com.siot.backend.checklist.domain;

/**
 * 사용자가 직접 표시한 항목 상태 (done[] / notApplicable[]).
 * TODO·OVERDUE 는 저장하지 않고 마감일과 오늘 날짜(Asia/Seoul)로 계산한다.
 */
public enum ProgressStatus {
    DONE,
    NOT_APPLICABLE
}
