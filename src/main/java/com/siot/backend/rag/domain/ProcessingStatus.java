package com.siot.backend.rag.domain;

/** ADM-04: 문서 버전 반영 상태 (대기·처리 중·완료·실패) */
public enum ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean inProgress() {
        return this == PENDING || this == PROCESSING;
    }
}
