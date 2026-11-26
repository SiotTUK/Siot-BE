-- NTF-03 알림 종류별로 받을 사전 알림 시점(D-n)을 여러 개 고른다 (예: D-7만, D-7·D-1).
-- NULL = 그 종류의 기본 시점 전부 (지금까지의 동작). 예전 lead_days(하나)는 발송에 쓰지 않는다.
ALTER TABLE notification_preference
    ADD COLUMN lead_days_list INTEGER[] NULL;

COMMENT ON COLUMN notification_preference.lead_days_list IS
    'NTF-03 받을 시점 D-n (STAY_EXPIRY: 120·60·30·14·7·1 중, CHECKLIST_DEADLINE·TAX: 7·1 중). NULL = 전부';
COMMENT ON COLUMN notification_preference.lead_days IS '예전 사전 알림 일수 (발송에 쓰지 않음, lead_days_list 사용)';
