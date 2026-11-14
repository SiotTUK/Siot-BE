-- CB-15: 답변을 질문 언어로 만든다. 대화 기록에서도 같은 언어로 연락처 라벨·담당 기관 안내를 보여 주기 위해 저장
ALTER TABLE chat_message
    ADD COLUMN language VARCHAR(10) NULL,
    ADD CONSTRAINT ck_chat_message_language CHECK (language IN ('ko', 'en', 'zh', 'vi'));

COMMENT ON COLUMN chat_message.language IS
    '답변만: 답한 언어 (질문 언어, 판별 못 하면 대화 언어, CB-15). NULL = 이 기능 이전 답변 → 대화 언어';
