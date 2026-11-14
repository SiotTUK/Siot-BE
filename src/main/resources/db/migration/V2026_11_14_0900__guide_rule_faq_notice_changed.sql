-- ---------------------------------------------------------------------
-- 생활수칙을 guide 에서 관리 (kind = RULE)
-- ---------------------------------------------------------------------
ALTER TABLE guide
    DROP CONSTRAINT ck_guide_kind,
    ADD CONSTRAINT ck_guide_kind CHECK (kind IN ('CHECKLIST', 'LIFE_CHANGE_TASK', 'RULE'));

COMMENT ON COLUMN guide.kind IS 'CHECKLIST(체크리스트 CL-xx) / LIFE_CHANGE_TASK(생활 변화 할 일 LC-xx-n) / RULE(생활수칙)';

-- RUL-04 자주 묻는 질문 TOP5: 생활수칙 중 is_faq 인 항목을 sort_order 순으로 보여 준다
ALTER TABLE guide
    ADD COLUMN is_faq BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_guide_faq_rule CHECK (NOT is_faq OR kind = 'RULE');

COMMENT ON COLUMN guide.is_faq IS 'RUL-04 자주 묻는 질문 TOP5 에 표시 (생활수칙만). 순서는 sort_order';

CREATE INDEX ix_guide_faq ON guide (sort_order) WHERE is_faq AND deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- NTF-07 안내 변경 표시
-- ---------------------------------------------------------------------
-- updated_at 은 오타 수정 같은 단순 수정에도 바뀌므로, 실제 안내 내용이 바뀐 시각을 따로 둔다
ALTER TABLE guide
    ADD COLUMN notice_changed_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN guide.notice_changed_at IS 'NTF-07 실제 안내 내용(기한·장소·준비물·수수료 등)이 바뀐 시각. NULL = 등록 후 바뀐 적 없음. 단순 수정에는 바꾸지 않음';
