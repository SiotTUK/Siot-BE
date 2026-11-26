-- NTF-02 행사 알림 받을 시점: 시작 1일(24시간)·3일·7일 전 중 하나. 기존 신청은 1일 전 (지금까지의 동작)
ALTER TABLE event_subscription
    ADD COLUMN lead_days INTEGER NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_event_subscription_lead_days CHECK (lead_days IN (1, 3, 7));

COMMENT ON COLUMN event_subscription.lead_days IS 'NTF-02 행사 시작 며칠 전에 알릴지 (1: 24시간 전, 3, 7: 그날 오전 9시)';

-- NTF-03·05 알림 종류별 이메일 수신 설정. 기본은 받음 (지금까지의 동작)
ALTER TABLE notification_preference
    ADD COLUMN email_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN notification_preference.email_enabled IS 'NTF-05 이 종류의 알림을 이메일로도 받을지 (인앱은 enabled)';

-- 이메일 수신을 꺼서 보내지 않은 알림은 SKIPPED
ALTER TABLE notification
    DROP CONSTRAINT ck_notification_email_status,
    ADD CONSTRAINT ck_notification_email_status CHECK (email_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'));
