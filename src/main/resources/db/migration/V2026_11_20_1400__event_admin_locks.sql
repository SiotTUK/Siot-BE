-- EVE-08: 관리자가 고친 지역·원문 링크·다문화 여부를 수집이 덮어쓰지 않게 한다
-- (다른 칸의 *_locked 는 V2 에 있다. 이 세 칸은 수집할 때마다 다시 계산돼 잠금이 필요하다)

ALTER TABLE event
    ADD COLUMN region_code_locked  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN source_url_locked   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN multicultural_locked BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN event.region_code_locked IS '관리자가 지역을 고침. 수집이 region_code 를 덮어쓰지 않는다';
COMMENT ON COLUMN event.source_url_locked IS '관리자가 원문 링크를 고침. 수집이 source_url 을 덮어쓰지 않는다';
COMMENT ON COLUMN event.multicultural_locked IS '관리자가 다문화 여부를 정함. 수집이 키워드로 다시 계산하지 않는다';
