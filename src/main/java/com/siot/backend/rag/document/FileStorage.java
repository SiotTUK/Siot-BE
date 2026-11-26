package com.siot.backend.rag.document;

/**
 * 업로드한 원본 파일 저장소 (ADM-02). 지금은 로컬 폴더, 나중에 GCS 구현을 추가한다.
 * 실패 버전 재실행(ADM-04)에 원본이 필요하므로 저장한 파일은 지우지 않는다.
 */
public interface FileStorage {

    /**
     * @param key 저장 위치 (예: documents/3/v2-uuid.pdf). 슬래시로 구분한 상대 경로
     */
    void save(String key, byte[] content);

    /** @throws java.io.UncheckedIOException 없거나 읽을 수 없음 */
    byte[] load(String key);

    /** 저장 직후 DB 기록이 실패했을 때만 쓴다 */
    void delete(String key);
}
