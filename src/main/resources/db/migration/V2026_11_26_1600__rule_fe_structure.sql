-- #14 생활수칙을 FE 하드코딩 구조(Siot-FE src/features/rules/content.ts)에 맞춘다.
-- 항목 코드는 FE 슬러그(order-noise 등), 카테고리는 FE 6개 분류와 순서, 문장 목록·시기·출처 목록·FAQ·확인일을 저장한다.

-- 언어별 문장 목록(points), 시기(when), 문의처(언어별), 사람이 확인한 번역 여부
ALTER TABLE guide_translation
    ADD COLUMN points   TEXT[]  NOT NULL DEFAULT '{}',
    ADD COLUMN timing   TEXT    NULL,
    ADD COLUMN contact  TEXT    NULL,
    ADD COLUMN reviewed BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN guide_translation.points IS '#14 확인된 문장 목록 (순서 유지, 생활수칙 본문)';
COMMENT ON COLUMN guide_translation.timing IS '#14 시기 (FE facts.when)';
COMMENT ON COLUMN guide_translation.contact IS '#14 언어별 문의처 문구. 없으면 guide.contact';
COMMENT ON COLUMN guide_translation.reviewed IS '#14 사람이 확인한 번역. true 면 자동 번역이 덮어쓰지 않는다';

-- 공식 페이지 확인일
ALTER TABLE guide
    ADD COLUMN checked_on DATE NULL;

COMMENT ON COLUMN guide.checked_on IS '#14 공식 페이지와 대조해 확인한 날 (FE rulesCheckedOn)';

-- 출처 목록 (이름·URL 여러 개, 순서)
CREATE TABLE guide_source (
    guide_id   BIGINT        NOT NULL,
    sort_order INTEGER       NOT NULL,
    name       VARCHAR(255)  NOT NULL,
    url        VARCHAR(2000) NOT NULL,
    CONSTRAINT pk_guide_source PRIMARY KEY (guide_id, sort_order),
    CONSTRAINT fk_guide_source_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id) ON DELETE CASCADE,
    CONSTRAINT ck_guide_source_url CHECK (url ~ '^https?://')
);

COMMENT ON TABLE guide_source IS '#14 항목 출처 목록 (공식 페이지 이름·URL, 순서)';

-- 생활수칙 카테고리: 한국어 이름(guide.category) ↔ FE 키, 화면 순서 (피그마 M06)
CREATE TABLE rule_category (
    category     VARCHAR(100) PRIMARY KEY,
    category_key VARCHAR(30)  NULL,
    sort_order   INTEGER      NOT NULL DEFAULT 1000,
    CONSTRAINT uk_rule_category_key UNIQUE (category_key)
);

COMMENT ON TABLE rule_category IS '#14 생활수칙 카테고리 키·순서. 관리자가 새로 만든 카테고리는 키 없이 뒤에';

-- FAQ: 질문·답변(언어별)과 연결 항목, 순서
CREATE TABLE rule_faq (
    faq_id     BIGSERIAL PRIMARY KEY,
    guide_id   BIGINT    NULL,
    sort_order INTEGER   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rule_faq_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id) ON DELETE SET NULL
);

CREATE TABLE rule_faq_translation (
    faq_id   BIGINT      NOT NULL,
    language VARCHAR(10) NOT NULL,
    question TEXT        NOT NULL,
    answer   TEXT        NOT NULL,
    CONSTRAINT pk_rule_faq_translation PRIMARY KEY (faq_id, language),
    CONSTRAINT fk_rule_faq_translation_faq FOREIGN KEY (faq_id) REFERENCES rule_faq (faq_id) ON DELETE CASCADE,
    CONSTRAINT ck_rule_faq_translation_language CHECK (language IN ('ko', 'en', 'zh', 'vi'))
);

CREATE INDEX idx_rule_faq_guide ON rule_faq (guide_id);

COMMENT ON TABLE rule_faq IS '#14 생활수칙 자주 묻는 질문 (RUL-04). guide_id = 자세히 볼 항목';
