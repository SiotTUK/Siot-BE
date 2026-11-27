-- BE #19 행사 사진 URL (FE thumbnailUrl, Figma M12·H1 카드, M07c 상세).
--   TourAPI firstimage(없으면 firstimage2), 경기도 문화행사(지지씨) thumbnail 을 수집한다. 전국문화축제표준데이터에는 사진이 없다.
-- 관리자가 고친 사진은 thumbnail_url_locked 로 수집이 덮어쓰지 않는다. 기존 행사는 다음 수집(매일 03시) 때 채운다.
ALTER TABLE event
    ADD COLUMN thumbnail_url        TEXT    NULL,
    ADD COLUMN thumbnail_url_locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_event_thumbnail_url CHECK (thumbnail_url IS NULL OR thumbnail_url ~* '^https?://');

COMMENT ON COLUMN event.thumbnail_url IS 'BE #19 행사 사진 URL (http/https). 없으면 화면은 분류별 기본 이미지';
COMMENT ON COLUMN event.thumbnail_url_locked IS '관리자가 사진을 고쳤으면 true (수집이 덮어쓰지 않음)';
