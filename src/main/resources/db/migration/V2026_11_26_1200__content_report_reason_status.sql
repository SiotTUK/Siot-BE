-- COM-01 신고 사유는 번역 오류 / 내용 오류 / 기타 셋 중 하나, ADM-07 상태는 접수 / 확인 중 / 처리 완료.
-- 이전에는 자유 문자열이라 값을 맞춘다. 코드로 바꿀 수 없는 사유는 OTHER 로 두고 원래 문구를 설명 앞에 남긴다.
UPDATE content_report SET reason = 'TRANSLATION' WHERE reason IN ('번역 오류', '번역오류', 'translation', 'TRANSLATION_ERROR');
UPDATE content_report SET reason = 'CONTENT'
WHERE reason IN ('내용 오류', '내용오류', '정보 오류', '정보오류', 'content', 'CONTENT_ERROR');
UPDATE content_report
SET description = '[이전 사유] ' || reason || COALESCE(E'\n' || description, ''),
    reason      = 'OTHER'
WHERE reason NOT IN ('TRANSLATION', 'CONTENT', 'OTHER');

UPDATE content_report SET status = upper(status) WHERE status <> upper(status);
UPDATE content_report SET status = 'RECEIVED' WHERE status NOT IN ('RECEIVED', 'IN_REVIEW', 'RESOLVED', 'REJECTED');

ALTER TABLE content_report
    ADD CONSTRAINT ck_content_report_reason CHECK (reason IN ('TRANSLATION', 'CONTENT', 'OTHER')),
    ADD CONSTRAINT ck_content_report_status CHECK (status IN ('RECEIVED', 'IN_REVIEW', 'RESOLVED', 'REJECTED'));

COMMENT ON COLUMN content_report.reason IS 'COM-01 TRANSLATION(번역 오류) / CONTENT(내용 오류) / OTHER(기타)';
COMMENT ON COLUMN content_report.status IS 'ADM-07 RECEIVED(접수) / IN_REVIEW(확인 중) / RESOLVED·REJECTED(처리 완료)';
