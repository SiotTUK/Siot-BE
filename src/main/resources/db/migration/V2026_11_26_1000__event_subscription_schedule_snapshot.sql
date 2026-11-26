-- NTF-02 일정 변경 알림: 회원이 마지막으로 안내받은(또는 신청할 때 본) 행사 일정.
-- 형식 "시작일|종료일|시작시각|종료시각" (없는 값은 빈 칸). 현재 일정과 다르면 변경 알림을 보내고 이 값을 갱신한다.
-- NULL 이면 (이 열이 생기기 전 신청) 다음 확인 때 알림 없이 현재 일정으로 채운다.
ALTER TABLE event_subscription
    ADD COLUMN schedule_snapshot VARCHAR(100) NULL;

COMMENT ON COLUMN event_subscription.schedule_snapshot IS 'NTF-02 마지막으로 안내한 행사 일정 (시작일|종료일|시작시각|종료시각)';
