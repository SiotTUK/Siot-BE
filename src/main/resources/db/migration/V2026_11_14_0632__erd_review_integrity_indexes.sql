-- =====================================================================
-- ERD 검토 반영 (2026-11-14). 운영 DB에 회원 데이터가 없는 시점에 적용한다.
--   1) 반복·재등록 항목의 완료 기록을 회차별로 구분 (checklist_progress.period_key)
--   2) 외래키 삭제 동작(ON DELETE) 보완
--   3) 중복 방지 UNIQUE / 부분 UNIQUE
--   4) notification 대상은 하나만
--   5) 조회·삭제용 인덱스 (PostgreSQL 은 FK 인덱스를 자동으로 만들지 않는다)
--   6) 사용하지 않는 것 삭제: refresh_token, app_user.display_name
--   7) 허용 값 CHECK
--   8) TIMESTAMP -> TIMESTAMPTZ (기존 값은 서울 시간으로 해석)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) checklist_progress: 회차 키
--   ''           한 번만 하는 항목 (CL-01 외국인등록 등)
--   'YYYY-MM'    매년 반복되는 세금의 납부 회차 (CL-10 '2026-06', '2026-12' / CL-11 '2026-07', '2026-09')
--   'YYYY-MM-DD' 생활 변화 할 일의 기준일 (LC-01 이사를 다시 등록하면 새 기준일의 할 일은 다시 TODO)
-- ---------------------------------------------------------------------
ALTER TABLE checklist_progress
    DROP CONSTRAINT pk_checklist_progress,
    ADD COLUMN period_key VARCHAR(10) NOT NULL DEFAULT '',
    ADD CONSTRAINT pk_checklist_progress PRIMARY KEY (user_id, guide_id, period_key),
    ADD CONSTRAINT ck_checklist_progress_period_key
        CHECK (period_key = '' OR period_key ~ '^[0-9]{4}-[0-9]{2}(-[0-9]{2})?$');

COMMENT ON COLUMN checklist_progress.period_key IS
    '회차: '''' = 한 번만 하는 항목, YYYY-MM = 세금 납부 회차, YYYY-MM-DD = 생활 변화 기준일';

-- ---------------------------------------------------------------------
-- 2) 외래키 삭제 동작
--   번역·구독·근거 연결·문서 하위 데이터: 부모와 함께 삭제 (CASCADE)
--   알림·신고·일정·답변 근거: 기록은 남기고 참조만 비움 (SET NULL)
-- ---------------------------------------------------------------------
ALTER TABLE event_translation
    DROP CONSTRAINT fk_event_translation_event,
    ADD CONSTRAINT fk_event_translation_event FOREIGN KEY (event_id) REFERENCES event (event_id) ON DELETE CASCADE;

ALTER TABLE event_subscription
    DROP CONSTRAINT fk_event_subscription_event,
    ADD CONSTRAINT fk_event_subscription_event FOREIGN KEY (event_id) REFERENCES event (event_id) ON DELETE CASCADE;

ALTER TABLE notification
    DROP CONSTRAINT fk_notification_event,
    DROP CONSTRAINT fk_notification_guide,
    DROP CONSTRAINT fk_notification_schedule,
    DROP CONSTRAINT fk_notification_document_version,
    ADD CONSTRAINT fk_notification_event FOREIGN KEY (event_id) REFERENCES event (event_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_notification_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_notification_schedule FOREIGN KEY (schedule_id) REFERENCES calendar_schedule (schedule_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_notification_document_version FOREIGN KEY (document_version_id) REFERENCES document_version (document_version_id) ON DELETE SET NULL;

ALTER TABLE content_report
    DROP CONSTRAINT fk_content_report_event,
    DROP CONSTRAINT fk_content_report_guide,
    ADD CONSTRAINT fk_content_report_event FOREIGN KEY (event_id) REFERENCES event (event_id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_content_report_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id) ON DELETE SET NULL;

ALTER TABLE calendar_schedule
    DROP CONSTRAINT fk_calendar_schedule_guide,
    ADD CONSTRAINT fk_calendar_schedule_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id) ON DELETE SET NULL;

ALTER TABLE guide_document_reference
    DROP CONSTRAINT fk_guide_document_reference_guide,
    DROP CONSTRAINT fk_guide_document_reference_version,
    ADD CONSTRAINT fk_guide_document_reference_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_guide_document_reference_version FOREIGN KEY (document_version_id) REFERENCES document_version (document_version_id) ON DELETE CASCADE;

ALTER TABLE document_version
    DROP CONSTRAINT fk_document_version_document,
    ADD CONSTRAINT fk_document_version_document FOREIGN KEY (document_id) REFERENCES document (document_id) ON DELETE CASCADE;

ALTER TABLE document_chunk
    DROP CONSTRAINT fk_document_chunk_version,
    ADD CONSTRAINT fk_document_chunk_version FOREIGN KEY (document_version_id) REFERENCES document_version (document_version_id) ON DELETE CASCADE;

-- 문서를 지워도 챗봇 답변의 근거 기록(순서·점수)은 남긴다
ALTER TABLE chat_message_source
    ALTER COLUMN chunk_id DROP NOT NULL,
    DROP CONSTRAINT fk_chat_message_source_chunk,
    ADD CONSTRAINT fk_chat_message_source_chunk FOREIGN KEY (chunk_id) REFERENCES document_chunk (chunk_id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------
-- 3) 중복 방지
-- ---------------------------------------------------------------------
-- 같은 회원에게 같은 알림(source_key)은 한 번만. source_key 가 NULL 이면 제한 없음
ALTER TABLE notification
    ADD CONSTRAINT uk_notification_user_source UNIQUE (user_id, source_key);

ALTER TABLE document_version
    ADD CONSTRAINT uk_document_version_no UNIQUE (document_id, version_no);

-- 문서당 현재 버전은 하나
CREATE UNIQUE INDEX uk_document_version_current ON document_version (document_id) WHERE is_current;

ALTER TABLE document_chunk
    ADD CONSTRAINT uk_document_chunk_no UNIQUE (document_version_id, chunk_no);

ALTER TABLE chat_message_source
    ADD CONSTRAINT uk_chat_message_source_order UNIQUE (message_id, source_order);

-- ---------------------------------------------------------------------
-- 4) notification 대상(행사·항목·일정·문서)은 하나만
-- ---------------------------------------------------------------------
ALTER TABLE notification
    ADD CONSTRAINT ck_notification_single_target
        CHECK (num_nonnulls(event_id, guide_id, schedule_id, document_version_id) <= 1);

-- ---------------------------------------------------------------------
-- 5) 인덱스
-- ---------------------------------------------------------------------
-- 목록 조회
CREATE INDEX idx_notification_user_created ON notification (user_id, created_at DESC);
CREATE INDEX idx_chat_conversation_user ON chat_conversation (user_id, updated_at DESC);
CREATE INDEX idx_chat_message_conversation ON chat_message (conversation_id, created_at);
CREATE INDEX idx_event_starts_on ON event (starts_on);
CREATE INDEX idx_content_report_status ON content_report (status, created_at DESC);
CREATE INDEX idx_chat_message_review ON chat_message (created_at) WHERE NOT is_reviewed;
-- 언어별 문구는 (language, guide_id) PK 라 guide_id 로 찾을 때 필요
CREATE INDEX idx_guide_translation_guide ON guide_translation (guide_id);
CREATE INDEX idx_event_translation_event ON event_translation (event_id);
-- 외래키 반대쪽 (부모 삭제·SET NULL 시 자식 행 탐색)
CREATE INDEX idx_checklist_progress_guide ON checklist_progress (guide_id);
CREATE INDEX idx_event_subscription_event ON event_subscription (event_id);
CREATE INDEX idx_content_report_reporter ON content_report (reporter_user_id);
CREATE INDEX idx_content_report_event ON content_report (event_id) WHERE event_id IS NOT NULL;
CREATE INDEX idx_content_report_guide ON content_report (guide_id) WHERE guide_id IS NOT NULL;
CREATE INDEX idx_notification_event ON notification (event_id) WHERE event_id IS NOT NULL;
CREATE INDEX idx_notification_guide ON notification (guide_id) WHERE guide_id IS NOT NULL;
CREATE INDEX idx_notification_schedule ON notification (schedule_id) WHERE schedule_id IS NOT NULL;
CREATE INDEX idx_notification_document_version ON notification (document_version_id) WHERE document_version_id IS NOT NULL;
CREATE INDEX idx_calendar_schedule_guide ON calendar_schedule (guide_id) WHERE guide_id IS NOT NULL;
CREATE INDEX idx_guide_document_reference_guide ON guide_document_reference (guide_id);
CREATE INDEX idx_guide_document_reference_version ON guide_document_reference (document_version_id);
CREATE INDEX idx_chat_message_source_chunk ON chat_message_source (chunk_id) WHERE chunk_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 6) 사용하지 않는 것 삭제
-- ---------------------------------------------------------------------
-- 인증은 세션(Redis) 방식이라 refresh_token 을 쓰지 않는다
DROP TABLE refresh_token;

-- 이름을 보여 주는 기능이 없고 가입 시 이메일을 복사할 뿐이다
ALTER TABLE app_user DROP COLUMN display_name;

-- ---------------------------------------------------------------------
-- 7) 허용 값
-- ---------------------------------------------------------------------
ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN'));

-- 새 종류를 추가하려면 이 제약을 바꾸는 마이그레이션을 함께 만든다
ALTER TABLE guide
    ADD CONSTRAINT ck_guide_kind CHECK (kind IN ('CHECKLIST', 'LIFE_CHANGE_TASK'));

ALTER TABLE event_translation
    ADD CONSTRAINT ck_event_translation_language CHECK (language IN ('ko', 'en', 'zh', 'vi'));
ALTER TABLE chat_conversation
    ADD CONSTRAINT ck_chat_conversation_language CHECK (language IN ('ko', 'en', 'zh', 'vi'));
ALTER TABLE content_report
    ADD CONSTRAINT ck_content_report_language CHECK (language IN ('ko', 'en', 'zh', 'vi'));
ALTER TABLE notification
    ADD CONSTRAINT ck_notification_locale CHECK (locale IN ('ko', 'en', 'zh', 'vi')),
    ADD CONSTRAINT ck_notification_email_status CHECK (email_status IN ('PENDING', 'SENT', 'FAILED'));

-- ---------------------------------------------------------------------
-- 8) TIMESTAMP -> TIMESTAMPTZ (시점을 시간대와 함께 저장)
-- ---------------------------------------------------------------------
DO $$
DECLARE
    col record;
BEGIN
    FOR col IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND data_type = 'timestamp without time zone'
          AND table_name <> 'flyway_schema_history'
    LOOP
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN %I TYPE TIMESTAMPTZ USING %I AT TIME ZONE ''Asia/Seoul''',
            col.table_name, col.column_name, col.column_name);
    END LOOP;
END $$;
