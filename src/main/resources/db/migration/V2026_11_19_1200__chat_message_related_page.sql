-- CB-10: 답변할 때 보여 준 '관련 내용 보기' 링크를 저장해, 대화 기록에서 같은 링크를 다시 보여 준다.
-- (전에는 기록을 볼 때마다 다시 계산해서 쿼리가 답변 수만큼 늘고, 행사 링크는 조회 날짜에 따라 바뀌었으며,
--  AI 오류 답변은 검색한 조각을 저장하지 않아 처음과 다른 링크가 나왔다)

CREATE TABLE chat_message_related_page (
    related_page_id BIGSERIAL   PRIMARY KEY,
    message_id      BIGINT      NOT NULL,
    sort_order      INTEGER     NOT NULL,
    page_type       VARCHAR(30) NOT NULL,
    code            VARCHAR(100) NOT NULL,
    title           TEXT        NOT NULL,
    CONSTRAINT fk_chat_message_related_page_message FOREIGN KEY (message_id) REFERENCES chat_message (message_id) ON DELETE CASCADE,
    -- page_type 은 guide.kind(역할 A) + EVENT 라서 CHECK 를 두지 않는다 (종류가 늘면 답변 저장이 실패하지 않도록)
    CONSTRAINT uq_chat_message_related_page_order UNIQUE (message_id, sort_order)
);

COMMENT ON TABLE chat_message_related_page IS
    '답변과 함께 보여 준 관련 서비스 화면 (CB-10). 제목은 답한 언어 기준, 저장 시점의 값';
COMMENT ON COLUMN chat_message_related_page.page_type IS 'CHECKLIST / LIFE_CHANGE_TASK / RULE (guide.kind) / EVENT';
COMMENT ON COLUMN chat_message_related_page.code IS '항목 ID (CL-01, LC-01-1 …). EVENT 는 event_id';

-- 이 기능 이전 답변은 저장된 링크가 없으므로 기록을 볼 때 예전처럼 계산한다 (FALSE)
ALTER TABLE chat_message
    ADD COLUMN related_pages_recorded BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN chat_message.related_pages_recorded IS
    '답변만: 관련 페이지를 chat_message_related_page 에 저장했는지 (CB-10). FALSE = 이 기능 이전 답변 → 조회 시 계산';
