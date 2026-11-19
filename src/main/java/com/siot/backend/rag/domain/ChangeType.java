package com.siot.backend.rag.domain;

/** ADM-02: 신규 문서 등록과 기존 문서 교체를 구분한다 (document_version.change_type) */
public enum ChangeType {
    /** 새 문서의 첫 버전 */
    NEW,
    /** 기존 문서의 새 버전 */
    REPLACE
}
