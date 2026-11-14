-- CB-11: 비자 발급 가능 여부·법률·의료 판단 질문에 담당 기관 안내를 함께 표시
-- (대화 기록에서도 다시 보여 주기 위해 저장)
ALTER TABLE chat_message
    ADD COLUMN referral_topics TEXT[] NOT NULL DEFAULT '{}',
    ADD CONSTRAINT ck_chat_message_referral_topics
        CHECK (referral_topics <@ ARRAY['VISA', 'LEGAL', 'MEDICAL']::TEXT[]);

COMMENT ON COLUMN chat_message.referral_topics IS
    '답변만: 질문이 판단을 요구해 담당 기관을 안내함 (CB-11). VISA(☎1345) / LEGAL(☎132) / MEDICAL(☎119). 빈 배열 = 없음';
