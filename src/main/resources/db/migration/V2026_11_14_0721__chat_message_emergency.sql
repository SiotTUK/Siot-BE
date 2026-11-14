-- CB-09: 응급 관련 질문에 대한 답변 표시 (대화 기록에서도 119/112 를 다시 보여 주기 위해 저장)
ALTER TABLE chat_message
    ADD COLUMN is_emergency BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN chat_message.is_emergency IS '답변만: 질문에 응급 키워드(화재·부상·범죄 등)가 있어 119/112 를 함께 표시함 (CB-09)';
