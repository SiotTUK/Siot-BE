package com.siot.backend.rag.dto;

/**
 * CB-10: 답변과 관련된 서비스 화면 ('관련 내용 보기' 버튼).
 *
 * @param type  CHECKLIST (체크리스트 항목) / LIFE_CHANGE_TASK (생활 변화 할 일)
 * @param code  항목 ID (CL-01, LC-01-1 ...). 프론트엔드가 이 값으로 상세 화면 경로를 만든다
 * @param title 대화 언어의 제목 (없으면 한국어)
 */
public record RelatedPage(String type, String code, String title) {
}
