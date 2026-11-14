-- =====================================================================
-- 체크리스트 규칙표 (백엔드 전달용, 2026-11-13) 반영
--
-- 저장하는 것 (DB 에만 저장, CHK-9)
--   * 온보딩 답 5개     : app_user.user_type / entry_date / purpose / stay_expiry + member_residence
--   * 사용자 기록       : app_user.language, checklist_progress(done[] / notApplicable[]),
--                         member_life_change(lifeChanges[] = { id, date })
--   * 항목 콘텐츠       : guide(CL-xx 체크리스트 항목, LC-xx-n 생활 변화 할 일) + 언어별 문구
--
-- 저장하지 않는 것 (요청마다 계산)
--   * 표시 조건 평가, 마감일·D-day, TODO / OVERDUE 상태, 진행률, homeMode·urgentCount·nextTask
--   * 표시 조건·기한 계산식·알림 시점은 규칙표대로 코드(checklist 규칙)에서 정의한다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 온보딩 답 (app_user)
-- ---------------------------------------------------------------------
-- 이전 정의서(입국 경로별 / 살고 있어요)의 값은 새 규칙과 맞지 않아 버린다.
-- 이사일은 생활 변화 LC-01 로, 지역은 온보딩에서 묻지 않는다 (행사는 모두에게 같음).
ALTER TABLE app_user
    DROP COLUMN report_type,
    DROP COLUMN employment_status,
    DROP COLUMN region_code,
    DROP COLUMN move_date;

-- F03 입국 날짜: 두 유형 모두 묻는다
ALTER TABLE app_user RENAME COLUMN arrival_date TO entry_date;

UPDATE app_user SET user_type = NULL WHERE user_type NOT IN ('NEW', 'LIVING');
UPDATE app_user SET language = 'ko' WHERE language NOT IN ('ko', 'en', 'zh', 'vi');

ALTER TABLE app_user
    ALTER COLUMN user_type TYPE VARCHAR(10),
    -- F04 체류 목적: 건너뛰면 NULL 로 저장하고 규칙 계산 때 OTHER 로 취급
    ADD COLUMN purpose VARCHAR(10) NULL,
    ADD CONSTRAINT ck_app_user_language CHECK (language IN ('ko', 'en', 'zh', 'vi')),
    -- F02 사용자 유형: NULL = 온보딩 전 (가입 직후 온보딩으로 이동)
    ADD CONSTRAINT ck_app_user_user_type CHECK (user_type IN ('NEW', 'LIVING')),
    ADD CONSTRAINT ck_app_user_purpose CHECK (purpose IN ('STUDY', 'WORK', 'FAMILY', 'OTHER'));

COMMENT ON COLUMN app_user.user_type   IS 'F02 사용자 유형: NEW(처음 입국) / LIVING(원래 살고 있음). NULL = 온보딩 전';
COMMENT ON COLUMN app_user.entry_date  IS 'F03 입국 날짜 (선택). 없으면 기한을 "OO일 이내" 로만 표시';
COMMENT ON COLUMN app_user.purpose     IS 'F04 체류 목적 (선택): STUDY / WORK / FAMILY / OTHER. NULL 은 OTHER 로 취급';
COMMENT ON COLUMN app_user.stay_expiry IS 'F06 체류 만료일 (선택). 없으면 CL-05 체류기간 연장을 만들지 않음';

-- F05 거주 정보 (복수 선택, 안 골라도 됨)
CREATE TABLE member_residence (
    user_id   BIGINT      NOT NULL,
    residence VARCHAR(10) NOT NULL,
    CONSTRAINT pk_member_residence PRIMARY KEY (user_id, residence),
    CONSTRAINT fk_member_residence_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_member_residence CHECK (residence IN ('KIDS', 'CAR', 'HOUSE', 'KOREAN', 'F4'))
);

COMMENT ON TABLE member_residence IS 'F05 거주 정보: KIDS(자녀) / CAR(자차) / HOUSE(자가) / KOREAN(한국어 공부) / F4(재외동포)';

-- ---------------------------------------------------------------------
-- 항목 콘텐츠 (guide)
-- ---------------------------------------------------------------------
-- code 를 API 의 항목 ID 로 쓴다 (CL-01, LC-01-1 ...)
-- (기존 데이터에 같은 code 가 있으면 나중 행의 code 뒤에 번호를 붙인다)
UPDATE guide g SET code = g.code || '-DUP-' || g.guide_id
WHERE EXISTS (SELECT 1 FROM guide o WHERE o.code = g.code AND o.guide_id < g.guide_id);

ALTER TABLE guide
    -- 진행률 대상 여부는 item_type 으로 판단한다 (REQUIRED 만 진행률에 포함)
    DROP COLUMN counts_progress,
    ADD COLUMN item_type        VARCHAR(10) NOT NULL DEFAULT 'REQUIRED',
    ADD COLUMN life_change_code VARCHAR(10) NULL,
    ADD CONSTRAINT uk_guide_code UNIQUE (code),
    ADD CONSTRAINT ck_guide_item_type CHECK (item_type IN ('REQUIRED', 'INFO', 'OPTIONAL')),
    ADD CONSTRAINT ck_guide_life_change CHECK (
        (kind = 'LIFE_CHANGE_TASK') = (life_change_code IS NOT NULL));

COMMENT ON COLUMN guide.code             IS '항목 ID. 체크리스트 CL-xx, 생활 변화 할 일 LC-xx-n';
COMMENT ON COLUMN guide.kind             IS 'CHECKLIST / LIFE_CHANGE_TASK';
COMMENT ON COLUMN guide.item_type        IS 'REQUIRED(필수, 진행률 포함) / INFO(안내, CL-13) / OPTIONAL(선택 생활 준비, CL-20~22)';
COMMENT ON COLUMN guide.life_change_code IS '생활 변화 할 일이 속한 변화 ID (LC-01 ~ LC-08)';
COMMENT ON COLUMN guide.contact          IS '문의 전화 (예: 1345, 1577-1000)';

-- CHK-06 항목 상세 필드 (문구는 언어별 저장: ko / en / zh / vi)
ALTER TABLE guide_translation
    ALTER COLUMN content DROP NOT NULL,
    ADD COLUMN badge       VARCHAR(100) NULL,
    ADD COLUMN place       TEXT         NULL,
    ADD COLUMN docs        TEXT[]       NULL,
    ADD COLUMN fee         VARCHAR(255) NULL,
    ADD COLUMN law         VARCHAR(255) NULL,
    ADD COLUMN penalty     TEXT         NULL,
    ADD COLUMN source_name VARCHAR(255) NULL,
    ADD CONSTRAINT ck_guide_translation_language CHECK (language IN ('ko', 'en', 'zh', 'vi'));

ALTER TABLE guide_translation
    DROP CONSTRAINT fk_guide_translation_guide,
    ADD CONSTRAINT fk_guide_translation_guide
        FOREIGN KEY (guide_id) REFERENCES guide (guide_id) ON DELETE CASCADE;

COMMENT ON COLUMN guide_translation.badge       IS '예: "입국 후 90일 이내"';
COMMENT ON COLUMN guide_translation.place       IS '신청 장소';
COMMENT ON COLUMN guide_translation.docs        IS '준비물 목록';
COMMENT ON COLUMN guide_translation.law         IS '근거 법령';
COMMENT ON COLUMN guide_translation.penalty     IS '안 하면 (과태료 등)';
COMMENT ON COLUMN guide_translation.source_name IS '출처 이름 (링크는 guide.source_url)';

-- 조건별 문구 (CL-04 건강보험: 체류 목적별 문구 + 공통 경고)
CREATE TABLE guide_variant_translation (
    guide_id    BIGINT      NOT NULL,
    language    VARCHAR(10) NOT NULL,
    variant_key VARCHAR(20) NOT NULL,
    body        TEXT        NOT NULL,
    CONSTRAINT pk_guide_variant_translation PRIMARY KEY (guide_id, language, variant_key),
    CONSTRAINT fk_guide_variant_translation_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id) ON DELETE CASCADE,
    CONSTRAINT ck_guide_variant_translation_language CHECK (language IN ('ko', 'en', 'zh', 'vi'))
);

COMMENT ON COLUMN guide_variant_translation.variant_key IS 'STUDY / WORK / FAMILY / OTHER / WARNING(공통 경고)';

-- ---------------------------------------------------------------------
-- 사용자 기록: done[] / notApplicable[]
-- ---------------------------------------------------------------------
-- TODO / OVERDUE 는 저장하지 않고 계산한다. 답을 바꿔도 기록은 지우지 않는다.
ALTER TABLE checklist_progress ADD COLUMN status VARCHAR(20) NULL;

UPDATE checklist_progress SET status = 'DONE'           WHERE is_completed;
UPDATE checklist_progress SET status = 'NOT_APPLICABLE' WHERE NOT is_completed AND NOT is_selected;
DELETE FROM checklist_progress WHERE status IS NULL;

ALTER TABLE checklist_progress
    DROP COLUMN is_selected,
    DROP COLUMN is_completed,
    DROP COLUMN completed_at,
    ALTER COLUMN status SET NOT NULL,
    ADD CONSTRAINT ck_checklist_progress_status CHECK (status IN ('DONE', 'NOT_APPLICABLE'));

ALTER TABLE checklist_progress
    DROP CONSTRAINT fk_checklist_progress_guide,
    ADD CONSTRAINT fk_checklist_progress_guide
        FOREIGN KEY (guide_id) REFERENCES guide (guide_id) ON DELETE CASCADE;

COMMENT ON TABLE checklist_progress IS '사용자가 표시한 항목 상태 (done[] / notApplicable[]). 기록이 없으면 TODO 또는 OVERDUE';

-- ---------------------------------------------------------------------
-- 사용자 기록: lifeChanges[] = { id, date }
-- ---------------------------------------------------------------------
-- 같은 변화를 다시 등록하면 기준일만 바꾼다 (한 변화당 1건)
CREATE TABLE member_life_change (
    user_id          BIGINT      NOT NULL,
    life_change_code VARCHAR(10) NOT NULL,
    base_date        DATE        NOT NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_member_life_change PRIMARY KEY (user_id, life_change_code),
    CONSTRAINT fk_member_life_change_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_member_life_change_code CHECK (life_change_code ~ '^LC-0[1-8]$')
);

COMMENT ON TABLE member_life_change IS 'F06a 생활 변화 등록. LC-01 이사 / LC-02 이직 / LC-03 입학 / LC-04 차 구입 / LC-05 출생 / LC-06 가족 입국 / LC-07 출국 / LC-08 이름·여권 변경';

-- ---------------------------------------------------------------------
-- 규칙표 항목 등록 (ID · 구분 · 한국어 제목 · 근거). 상세 문구는 관리자 화면에서 채운다.
-- ---------------------------------------------------------------------
INSERT INTO guide (code, kind, item_type, life_change_code, category, sort_order, contact) VALUES
    ('CL-01', 'CHECKLIST', 'REQUIRED', NULL, 'IMMIGRATION', 10, '1345'),
    ('CL-02', 'CHECKLIST', 'REQUIRED', NULL, 'IMMIGRATION', 20, '1345'),
    ('CL-04', 'CHECKLIST', 'REQUIRED', NULL, 'HEALTH_INSURANCE', 40, '1577-1000'),
    ('CL-05', 'CHECKLIST', 'REQUIRED', NULL, 'IMMIGRATION', 50, '1345'),
    ('CL-06', 'CHECKLIST', 'REQUIRED', NULL, 'IMMIGRATION', 60, '1345'),
    ('CL-07', 'CHECKLIST', 'REQUIRED', NULL, 'IMMIGRATION', 70, '1345'),
    ('CL-08', 'CHECKLIST', 'REQUIRED', NULL, 'FAMILY', 80, NULL),
    ('CL-09', 'CHECKLIST', 'REQUIRED', NULL, 'IMMIGRATION', 90, '1345'),
    ('CL-10', 'CHECKLIST', 'REQUIRED', NULL, 'TAX', 100, NULL),
    ('CL-11', 'CHECKLIST', 'REQUIRED', NULL, 'TAX', 110, NULL),
    ('CL-12', 'CHECKLIST', 'REQUIRED', NULL, 'EDUCATION', 120, NULL),
    ('CL-13', 'CHECKLIST', 'INFO',     NULL, 'IMMIGRATION', 130, '1345'),
    ('CL-20', 'CHECKLIST', 'OPTIONAL', NULL, 'SETTLING', 200, NULL),
    ('CL-21', 'CHECKLIST', 'OPTIONAL', NULL, 'SETTLING', 210, NULL),
    ('CL-22', 'CHECKLIST', 'OPTIONAL', NULL, 'SETTLING', 220, NULL),
    ('LC-01-1', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-01', 'IMMIGRATION', 1, '1345'),
    ('LC-01-2', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-01', 'IMMIGRATION', 2, '1345'),
    ('LC-01-3', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-01', 'CAR', 3, NULL),
    ('LC-01-4', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-01', 'EDUCATION', 4, NULL),
    ('LC-01-5', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-01', 'SETTLING', 5, NULL),
    ('LC-02-1', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-02', 'EMPLOYMENT', 1, NULL),
    ('LC-02-2', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-02', 'IMMIGRATION', 2, '1345'),
    ('LC-02-3', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-02', 'IMMIGRATION', 3, '1345'),
    ('LC-02-4', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-02', 'HEALTH_INSURANCE', 4, '1577-1000'),
    ('LC-03-1', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-03', 'EDUCATION', 1, NULL),
    ('LC-04-1', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-04', 'CAR', 1, NULL),
    ('LC-04-2', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-04', 'CAR', 2, NULL),
    ('LC-05-1', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-05', 'IMMIGRATION', 1, '1345'),
    ('LC-06-1', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-06', 'IMMIGRATION', 1, '1345'),
    ('LC-07-1', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-07', 'IMMIGRATION', 1, '1345'),
    ('LC-08-1', 'LIFE_CHANGE_TASK', 'REQUIRED', 'LC-08', 'IMMIGRATION', 1, '1345')
ON CONFLICT (code) DO NOTHING;

INSERT INTO guide_translation (language, guide_id, title, law)
SELECT 'ko', g.guide_id, t.title, t.law
FROM (VALUES
    ('CL-01', '외국인등록', '출입국관리법 제31조'),
    ('CL-02', '국내거소신고', '재외동포법 제6조'),
    ('CL-04', '건강보험 가입 확인', '국민건강보험법 제109조'),
    ('CL-05', '체류기간 연장', '출입국관리법 제25조'),
    ('CL-06', '아르바이트 전 시간제 취업 허가', '출입국관리법 제20조'),
    ('CL-07', '직장을 바꿀 때 절차 알아두기', '출입국관리법 제21조'),
    ('CL-08', '가족센터 프로그램 보기', NULL),
    ('CL-09', '체류·취업 업종 상담 (☎1345)', NULL),
    ('CL-10', '자동차세 내기', '지방세법'),
    ('CL-11', '재산세 내기', '지방세법'),
    ('CL-12', '아이 학교 입학·전학 안내', NULL),
    ('CL-13', '이사하면 거소 이전 신고 (안내)', '재외동포법 제6조'),
    ('CL-20', '휴대폰 개통', NULL),
    ('CL-21', '은행 계좌 만들기', NULL),
    ('CL-22', '쓰레기 분리배출 요일 확인', NULL),
    ('LC-01-1', '체류지 변경 신고', '출입국관리법 제36조'),
    ('LC-01-2', '거소 이전 신고', '재외동포법 제6조'),
    ('LC-01-3', '자동차 주소 변경', '자동차등록령 제22조'),
    ('LC-01-4', '자녀 전학 안내', NULL),
    ('LC-01-5', '새 동네 쓰레기 배출 요일 확인', NULL),
    ('LC-02-1', '고용센터 사업장 변경', '외국인근로자고용법'),
    ('LC-02-2', '근무처 변경 신고', '출입국관리법 제21조'),
    ('LC-02-3', '새 사업장 시간제 취업 허가', '출입국관리법 제20조'),
    ('LC-02-4', '건강보험 직장 변경 확인', NULL),
    ('LC-03-1', '희망 학교에 입학 신청', NULL),
    ('LC-04-1', '중고차 이전등록', '자동차관리법 제12조'),
    ('LC-04-2', '자동차보험 가입', NULL),
    ('LC-05-1', '아기 체류자격 부여 신청', '출입국관리법 제23조'),
    ('LC-06-1', '가족 외국인등록', '출입국관리법 제31조'),
    ('LC-07-1', '재입국허가 확인', '출입국관리법 제30조'),
    ('LC-08-1', '외국인등록사항 변경 신고', '출입국관리법 제35조')
) AS t (code, title, law)
JOIN guide g ON g.code = t.code
ON CONFLICT (language, guide_id) DO NOTHING;

-- CL-04 건강보험 조건별 문구 (화면 문구에는 비자 코드를 쓰지 않는다)
INSERT INTO guide_variant_translation (guide_id, language, variant_key, body)
SELECT g.guide_id, 'ko', v.variant_key, v.body
FROM (VALUES
    ('STUDY',   '대학·대학원·초중고 학생은 입국일부터 자동 가입돼요. 어학연수생은 한국에 6개월 머문 뒤 가입돼요.'),
    ('WORK',    '고용허가제로 온 근로자는 입국일부터 가입돼요. 그 밖의 회사원은 회사가 직장 보험에 가입해 줘요.'),
    ('FAMILY',  '한국인과 결혼해서 온 사람은 입국일부터 자동 가입돼요. 그 밖의 가족은 체류 자격에 따라 달라서 ☎1577-1000에서 확인하세요.'),
    ('OTHER',   '영주권이 있는 사람은 입국일부터 가입돼요. 그 밖에는 한국에 6개월 머문 뒤 가입돼요.'),
    ('WARNING', '보험료를 50만 원 넘게 못 내면 체류 연장이 제한될 수 있어요. 문의 ☎1577-1000 (외국어 ☎033-811-2000)')
) AS v (variant_key, body)
JOIN guide g ON g.code = 'CL-04'
ON CONFLICT DO NOTHING;
