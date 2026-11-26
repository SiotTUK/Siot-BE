-- RUL-12, RUL-13: 생활수칙 카테고리명 번역.
-- 카테고리는 guide.category (한국어 이름) 로 분류하고, 언어별 이름을 여기에 둔다.
-- 생활수칙을 저장할 때 없는 언어는 자동 번역으로 채우고, 관리자가 고칠 수 있다.
CREATE TABLE rule_category_translation (
    category   VARCHAR(100) NOT NULL,
    language   VARCHAR(10)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_rule_category_translation PRIMARY KEY (category, language),
    CONSTRAINT ck_rule_category_translation_language CHECK (language IN ('en', 'zh', 'vi'))
);

COMMENT ON TABLE rule_category_translation IS 'RUL-13 생활수칙 카테고리명 번역 (원문은 guide.category 한국어 이름)';
