-- EVE-10 행사 카테고리 (2026-11-26 결정). 출처 분류를 그대로 옮긴다:
--   지지씨 category 공연/전시/교육/행사 → PERFORMANCE/EXHIBITION/EDUCATION/EVENT
--   TourAPI lclsSystm2 EV01/EV02/EV03 → FESTIVAL/PERFORMANCE/EVENT, 전국문화축제표준데이터 → FESTIVAL
-- 모르는 값은 EVENT. 관리자가 고친 카테고리는 category_locked 로 수집이 덮어쓰지 않는다.
ALTER TABLE event
    ADD COLUMN category        VARCHAR(20) NOT NULL DEFAULT 'EVENT',
    ADD COLUMN category_locked BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_event_category
        CHECK (category IN ('FESTIVAL', 'PERFORMANCE', 'EXHIBITION', 'EDUCATION', 'EVENT'));

-- 이미 있는 행사: 축제표준데이터는 모두 축제. 지지씨·TourAPI 행사는 다음 수집(매일 03시) 때 출처 분류로 채운다
UPDATE event SET category = 'FESTIVAL' WHERE external_source = 'FESTIVAL_STD';

CREATE INDEX idx_event_category ON event (category);

COMMENT ON COLUMN event.category IS 'EVE-10 카테고리 FESTIVAL/PERFORMANCE/EXHIBITION/EDUCATION/EVENT';
COMMENT ON COLUMN event.category_locked IS '관리자가 카테고리를 고쳤으면 true (수집이 덮어쓰지 않음)';
