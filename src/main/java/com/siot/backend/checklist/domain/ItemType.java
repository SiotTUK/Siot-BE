package com.siot.backend.checklist.domain;

/** 항목 구분. 진행률은 REQUIRED 만 센다 (완료 ÷ 표시 중인 필수 항목 중 해당 없음을 뺀 것). */
public enum ItemType {
    /** 필수 */
    REQUIRED,
    /** 안내 (CL-13) */
    INFO,
    /** 선택 생활 준비 (CL-20~22) */
    OPTIONAL
}
