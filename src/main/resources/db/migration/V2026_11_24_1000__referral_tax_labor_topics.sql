-- CB-11: 세금·노동·산업재해 판단 질문에도 담당 기관 안내 (시흥시 외국인주민 생활안내 37쪽 연락처)
ALTER TABLE chat_message
    DROP CONSTRAINT ck_chat_message_referral_topics,
    ADD CONSTRAINT ck_chat_message_referral_topics
        CHECK (referral_topics <@ ARRAY['VISA', 'LEGAL', 'MEDICAL', 'TAX', 'LABOR', 'WORK_INJURY']::TEXT[]);

COMMENT ON COLUMN chat_message.referral_topics IS
    '답변만: 질문이 판단을 요구해 담당 기관을 안내함 (CB-11). VISA(☎1345) / LEGAL(☎132) / MEDICAL(☎119) / TAX(☎126) / LABOR(☎1350) / WORK_INJURY(☎1588-0075). 빈 배열 = 없음';
