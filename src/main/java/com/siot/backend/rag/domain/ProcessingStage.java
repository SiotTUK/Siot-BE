package com.siot.backend.rag.domain;

/**
 * ADM-04: 반영 단계. 실패하면 processing_error 앞에 "[단계] " 로 남긴다 (챗봇과 합의한 형식).
 */
public enum ProcessingStage {
    /** PDF 텍스트 추출 */
    EXTRACT,
    /** 조각 나누기 */
    SPLIT,
    /** 임베딩 */
    EMBEDDING,
    /** 조각 저장·버전 전환 */
    INDEX;

    public String tag() {
        return "[" + name() + "]";
    }

    /** "[EMBEDDING] 이유" → EMBEDDING. 형식이 다르면 null */
    public static ProcessingStage fromError(String processingError) {
        if (processingError == null || !processingError.startsWith("[")) {
            return null;
        }
        int end = processingError.indexOf(']');
        if (end < 0) {
            return null;
        }
        try {
            return valueOf(processingError.substring(1, end));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** "[EMBEDDING] 이유" → "이유" */
    public static String reasonOf(String processingError) {
        if (fromError(processingError) == null) {
            return processingError;
        }
        return processingError.substring(processingError.indexOf(']') + 1).strip();
    }
}
