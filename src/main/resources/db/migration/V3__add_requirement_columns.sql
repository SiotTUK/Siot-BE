-- =====================================================================
-- 요구사항 대비 ERD 누락 컬럼 보완
-- =====================================================================

-- M-11 / NTF-09: 인증된 이메일이 있는 회원만 비밀번호 재설정 메일 발송
ALTER TABLE app_user
    ADD COLUMN email_verified_at TIMESTAMP NULL;

-- CHK-09 / CHK-11 / CHK-12 / COM-03: 비회원 기기 값과 계정 값을 항목별로 비교해
-- 더 최근 값을 남기기 위한 항목별 수정 시각 (완료 체크는 checklist_progress.updated_at 사용)
ALTER TABLE app_user
    ADD COLUMN language_updated_at     TIMESTAMP NULL,
    ADD COLUMN user_type_updated_at    TIMESTAMP NULL,
    ADD COLUMN region_code_updated_at  TIMESTAMP NULL,
    ADD COLUMN arrival_date_updated_at TIMESTAMP NULL,
    ADD COLUMN move_date_updated_at    TIMESTAMP NULL,
    ADD COLUMN stay_expiry_updated_at  TIMESTAMP NULL;

-- COM-01: 신고 시 화면 언어 저장
ALTER TABLE content_report
    ADD COLUMN language VARCHAR(10) NOT NULL DEFAULT 'ko';

-- ADM-08: 부정 평가 답변 / 미응답 질문의 관리자 처리 여부와 메모
ALTER TABLE chat_message
    ADD COLUMN is_reviewed BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN admin_memo  TEXT      NULL,
    ADD COLUMN reviewed_by BIGINT    NULL,
    ADD COLUMN reviewed_at TIMESTAMP NULL,
    ADD CONSTRAINT fk_chat_message_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES app_user (user_id) ON DELETE SET NULL;
