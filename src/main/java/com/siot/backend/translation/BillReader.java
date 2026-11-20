package com.siot.backend.translation;

import com.siot.backend.rag.ai.AiUnavailableException;

/**
 * 고지서 사진 읽기 (RUL-07). 사진 한 장을 AI 로 읽어 항목과 사용자 언어 요약을 돌려준다.
 * 사진·결과를 저장하거나 로그에 남기지 않는다.
 */
public interface BillReader {

    /**
     * @param image    JPEG 또는 PNG (PDF 는 호출하는 쪽에서 첫 쪽을 이미지로 바꿔 넘긴다)
     * @param mimeType image/jpeg, image/png
     * @param language 요약을 쓸 언어 (ko, en, zh, vi)
     * @throws AiUnavailableException 키가 없거나 호출·응답 형식이 실패했을 때
     */
    BillReading read(byte[] image, String mimeType, String language);
}
