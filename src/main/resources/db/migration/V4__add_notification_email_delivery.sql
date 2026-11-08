-- =====================================================================
-- NTF-05: 알림 채널 = 서비스 내 알림 목록 + 등록 이메일
-- 기존 status / attempts / last_error / delivered_at 은 서비스 내 알림 생성 기준이며,
-- 이메일 발송 결과는 별도 컬럼으로 기록한다.
--   email_status: NULL = 이메일 발송 대상 아님 (인증된 이메일 없음)
--                 PENDING / SENT / FAILED
-- =====================================================================
ALTER TABLE notification
    ADD COLUMN email_status     VARCHAR(30) NULL,
    ADD COLUMN email_attempts   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN email_last_error TEXT        NULL,
    ADD COLUMN email_sent_at    TIMESTAMP   NULL;

-- 발송 대기 알림 조회용 (예약 발송 스케줄러)
CREATE INDEX idx_notification_pending ON notification (scheduled_at) WHERE status = 'PENDING';
CREATE INDEX idx_notification_email_pending ON notification (scheduled_at) WHERE email_status = 'PENDING';
