-- =====================================================================
-- 요구사항 수정 (체크리스트 규칙표 11/13 결정 반영)
--   ① 회원가입하지 않으면 아무것도 못 봄      → 비회원 식별자(guest_session_id) 삭제
--   ② 아이디 = 이메일                         → login_id 삭제, email 을 로그인 ID(UNIQUE)로 사용
--   CHK-9 비로그인 기기 저장·병합 삭제        → 항목별 *_updated_at 삭제
--
-- 유지: 이메일 인증(email_verified_at), M-11 비밀번호 재설정, NTF-05 이메일 알림(notification.email_*)
--   가입 이메일 = 인증 대상. 인증 전에는 비밀번호 재설정·이메일 알림을 보내지 않는다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- app_user: 로그인 ID = 이메일
-- ---------------------------------------------------------------------
-- 기존 회원 이메일을 소문자로 맞춘다 (앱은 항상 소문자로 저장·조회)
UPDATE app_user SET email = lower(trim(email)) WHERE email IS NOT NULL;

-- 이메일이 없거나 다른 회원과 겹치는 기존 계정은 임시 주소를 넣고 미인증으로 둔다
-- (겹치면 가장 먼저 가입한 회원이 주소를 가진다)
UPDATE app_user u
SET email = u.login_id || '@legacy.siot.invalid',
    email_verified_at = NULL
WHERE u.email IS NULL
   OR u.email = ''
   OR EXISTS (SELECT 1 FROM app_user o WHERE o.email = u.email AND o.user_id < u.user_id);

ALTER TABLE app_user
    DROP CONSTRAINT uk_app_user_login_id,
    DROP COLUMN login_id,
    ALTER COLUMN email SET NOT NULL,
    ADD CONSTRAINT uk_app_user_email UNIQUE (email),
    -- CHK-9 비회원 값 병합용 항목별 수정 시각
    DROP COLUMN language_updated_at,
    DROP COLUMN user_type_updated_at,
    DROP COLUMN region_code_updated_at,
    DROP COLUMN arrival_date_updated_at,
    DROP COLUMN move_date_updated_at,
    DROP COLUMN stay_expiry_updated_at;

COMMENT ON COLUMN app_user.email IS '로그인 ID (소문자). 인증 코드 확인 후 email_verified_at 기록';
COMMENT ON COLUMN app_user.email_verified_at IS '이메일 인증 시각. NULL 이면 비밀번호 재설정·이메일 알림 대상 아님';

-- ---------------------------------------------------------------------
-- 비회원 데이터 삭제
-- ---------------------------------------------------------------------
-- 챗봇 대화는 회원만 (CB-03: 모두 회원이라 전원 이전 기록 보기 가능)
DELETE FROM chat_conversation WHERE user_id IS NULL;

ALTER TABLE chat_conversation
    DROP COLUMN guest_session_id,
    ALTER COLUMN user_id SET NOT NULL;

-- 오류 신고는 회원만 작성. 탈퇴 시 reporter_user_id 는 SET NULL 로 남는다
ALTER TABLE content_report
    DROP COLUMN guest_session_id;
