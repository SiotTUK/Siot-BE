-- RUL-07 고지서 번역: 최근 번역 저장과 납부 마감 알림.
-- 사진은 여전히 저장하지 않는다. 개인정보를 가린 읽기 결과(응답 JSON)만 회원당 최근 10개까지 남긴다.
CREATE TABLE bill_translation (
    bill_translation_id BIGSERIAL   PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    language            VARCHAR(10) NOT NULL,
    document_type       VARCHAR(30) NULL,
    issuer              TEXT        NULL,
    tax_type            TEXT        NULL,
    amount              BIGINT      NULL,
    due_date            DATE        NULL,
    result              TEXT        NOT NULL,
    reminder            BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_translation_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_bill_translation_language CHECK (language IN ('ko', 'en', 'zh', 'vi')),
    CONSTRAINT ck_bill_translation_reminder CHECK (NOT reminder OR due_date IS NOT NULL)
);

CREATE INDEX idx_bill_translation_user_created ON bill_translation (user_id, created_at DESC);
CREATE INDEX idx_bill_translation_reminder_due ON bill_translation (due_date) WHERE reminder;

COMMENT ON TABLE bill_translation IS 'RUL-07 최근 고지서 번역 (사진 없음, 개인정보 가린 결과만, 회원당 최근 10개)';
COMMENT ON COLUMN bill_translation.result IS '번역 결과 JSON (BillTranslationResponse, id·reminder·savedAt 제외)';
COMMENT ON COLUMN bill_translation.reminder IS '납부 마감 D-7·D-1 알림 신청 여부 (due_date 가 있어야 켤 수 있다)';

-- 마감 알림을 누르면 그 번역 결과로 간다
ALTER TABLE notification
    ADD COLUMN bill_translation_id BIGINT NULL,
    ADD CONSTRAINT fk_notification_bill_translation FOREIGN KEY (bill_translation_id)
        REFERENCES bill_translation (bill_translation_id) ON DELETE SET NULL,
    DROP CONSTRAINT ck_notification_single_target,
    ADD CONSTRAINT ck_notification_single_target
        CHECK (num_nonnulls(event_id, guide_id, schedule_id, document_version_id, bill_translation_id) <= 1);

CREATE INDEX idx_notification_bill_translation ON notification (bill_translation_id);
