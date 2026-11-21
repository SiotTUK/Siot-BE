package com.siot.backend.rag.domain;

/** ADM-02·04: 신규 문서 등록, 기존 문서 교체, 같은 파일 재처리를 구분한다 (document_version.change_type) */
public enum ChangeType {
    /** 새 문서의 첫 버전 */
    NEW,
    /** 기존 문서의 새 버전 */
    REPLACE,
    /**
     * ADM-04: 현재 버전의 같은 파일을 지금 조각 규칙으로 다시 반영. 문서 내용은 바뀌지 않았으므로
     * 사용자 대상 문서 변경 알림을 만든다면 이 유형은 빼야 한다
     */
    REPROCESS
}
