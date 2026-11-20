-- EVE-01, EVE-02: 행사 자동 수집
-- 1) 번역이 어느 원문 revision 기준인지 (원문이 바뀌면 다시 번역, COM-02)
-- 2) 추천 태그 (EVE-05: 거주 형태·관심사와 맞추는 키워드 태그)
-- 3) 출처별 수집 실행 기록 (EVE-10: 최종 갱신 시각 = 마지막 SUCCEEDED 의 finished_at)

ALTER TABLE event_translation
    ADD COLUMN source_revision INTEGER NULL;

COMMENT ON COLUMN event_translation.source_revision IS
    '번역(또는 ko 원문)을 만들 때의 event.revision. event.revision 과 다르면 다시 번역한다';

CREATE TABLE event_tag (
    event_id BIGINT      NOT NULL,
    tag      VARCHAR(30) NOT NULL,
    CONSTRAINT pk_event_tag PRIMARY KEY (event_id, tag),
    CONSTRAINT fk_event_tag_event FOREIGN KEY (event_id) REFERENCES event (event_id) ON DELETE CASCADE,
    CONSTRAINT ck_event_tag_tag CHECK (tag IN ('FAMILY_KIDS', 'KOREAN_CLASS', 'NEWCOMER'))
);

COMMENT ON TABLE event_tag IS '추천 태그. FAMILY_KIDS(자녀 동반), KOREAN_CLASS(한국어 교육), NEWCOMER(새로 온 사람). 수집 시 행사명 키워드로 붙인다';

CREATE TABLE event_collection_run (
    run_id         BIGSERIAL   PRIMARY KEY,
    source         VARCHAR(30) NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL,
    finished_at    TIMESTAMPTZ NULL,
    status         VARCHAR(20) NOT NULL,
    fetched_count  INTEGER     NOT NULL DEFAULT 0,
    upserted_count INTEGER     NOT NULL DEFAULT 0,
    error          TEXT        NULL,
    CONSTRAINT ck_event_collection_run_source CHECK (source IN ('GGC', 'TOURAPI', 'FESTIVAL_STD')),
    CONSTRAINT ck_event_collection_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
);

COMMENT ON TABLE event_collection_run IS '행사 수집 실행 기록 (출처별). 실패해도 기존 행사는 그대로 둔다';
COMMENT ON COLUMN event_collection_run.fetched_count IS '출처에서 받은 행사 수 (거르기 전)';
COMMENT ON COLUMN event_collection_run.upserted_count IS '새로 만들거나 내용이 바뀐 행사 수';

CREATE INDEX idx_event_collection_run_last ON event_collection_run (source, status, finished_at DESC);
