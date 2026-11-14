# DB ERD — 체크리스트 규칙표 반영 (2026-11-13)

기준 문서: **체크리스트 규칙표 (백엔드 전달용, 11/13)**. 마이그레이션:

- `V2026_11_14_0425__email_login_and_member_only.sql` — 아이디 = 이메일, 비회원 데이터 삭제 (이메일 인증·비밀번호 재설정·이메일 알림은 유지)
- `V2026_11_14_0430__checklist_rules_onboarding.sql` — 온보딩 답 5개, 거주 정보, 완료/해당 없음, 생활 변화, 항목 상세(CHK-06), 규칙표 항목 등록

## 원칙

| 저장한다 (DB에만, CHK-9) | 저장하지 않는다 (요청마다 계산) |
|---|---|
| 온보딩 답 5개 + language | 표시 조건 평가 결과 |
| done[] / notApplicable[] | 마감일·D-day, TODO / OVERDUE |
| lifeChanges[] = { id, date } | 진행률, homeMode · urgentCount · nextTask |
| 항목 콘텐츠 (언어별 문구) | 정렬 순서 |

표시 조건·기한 계산식·알림 시점은 규칙표대로 **코드에서 정의**하고 `guide.code`(CL-01, LC-01-1 …)로 콘텐츠와 연결한다.
오늘 날짜는 서울 시간(Asia/Seoul) 기준이다.

## ERD (변경된 부분)

```mermaid
erDiagram
    app_user ||--o{ member_residence : "F05 거주 정보"
    app_user ||--o{ checklist_progress : "done / notApplicable"
    app_user ||--o{ member_life_change : "lifeChanges"
    app_user ||--o{ notification : ""
    app_user ||--o{ chat_conversation : ""
    guide ||--o{ guide_translation : "ko/en/zh/vi"
    guide ||--o{ guide_variant_translation : "조건별 문구"
    guide ||--o{ checklist_progress : ""
    guide ||--o{ notification : ""

    app_user {
        bigint user_id PK
        varchar email UK "로그인 ID, 소문자"
        timestamp email_verified_at "NULL=미인증"
        varchar password_hash
        varchar display_name
        varchar role
        varchar language "ko/en/zh/vi"
        varchar user_type "F02 NEW/LIVING, NULL=온보딩 전"
        date entry_date "F03 (선택)"
        varchar purpose "F04 STUDY/WORK/FAMILY/OTHER, NULL=OTHER"
        date stay_expiry "F06 (선택)"
        timestamp created_at
        timestamp updated_at
    }
    member_residence {
        bigint user_id PK,FK
        varchar residence PK "KIDS/CAR/HOUSE/KOREAN/F4"
    }
    guide {
        bigint guide_id PK
        varchar code UK "CL-01, LC-01-1"
        varchar kind "CHECKLIST / LIFE_CHANGE_TASK"
        varchar item_type "REQUIRED/INFO/OPTIONAL"
        varchar life_change_code "LC-01~LC-08 (할 일만)"
        varchar category
        int sort_order
        text source_url "출처 링크"
        varchar contact "문의 전화"
        timestamp updated_at "CHK-06 updatedAt"
    }
    guide_translation {
        varchar language PK
        bigint guide_id PK,FK
        varchar title
        varchar badge "예: 입국 후 90일 이내"
        text place
        text_array docs "준비물"
        varchar fee
        varchar law "근거"
        text penalty "안 하면"
        varchar source_name "출처 이름"
        text summary
        text content
    }
    guide_variant_translation {
        bigint guide_id PK,FK
        varchar language PK
        varchar variant_key PK "STUDY/WORK/FAMILY/OTHER/WARNING"
        text body
    }
    checklist_progress {
        bigint user_id PK,FK
        bigint guide_id PK,FK
        varchar status "DONE / NOT_APPLICABLE"
        timestamp updated_at
    }
    member_life_change {
        bigint user_id PK,FK
        varchar life_change_code PK "LC-01~LC-08"
        date base_date "기준일"
        timestamp created_at
        timestamp updated_at
    }
```

변경 없는 테이블: `calendar_schedule`, `event*`, `document*`, `guide_document_reference`, `chat_message*`, `notification_preference`, `refresh_token`.

## 규칙표 ↔ 테이블

| 규칙표 | 저장 위치 |
|---|---|
| userType (F02, 필수) | `app_user.user_type` — NULL 이면 가입 직후 온보딩으로 이동 |
| entryDate (F03) | `app_user.entry_date` — 없으면 기한을 "OO일 이내"로만 표시, 알림 없음 |
| purpose (F04) | `app_user.purpose` — 건너뛰면 NULL, 계산 시 OTHER |
| residence[] (F05) | `member_residence` (행 없음 = 안 고름) |
| stayExpiry (F06) | `app_user.stay_expiry` — 없으면 CL-05 대신 입력 안내만 |
| language | `app_user.language` |
| done[] / notApplicable[] | `checklist_progress.status` — 답을 바꿔도 지우지 않음 |
| lifeChanges[] { id, date } | `member_life_change` — 같은 변화 재등록 시 `base_date` 만 변경 |
| 항목 상세 (CHK-06) id, title, badge, place, docs[], fee, law, penalty, source, updatedAt | `guide.code`, `guide_translation.*`, `guide.source_url`, `guide.updated_at` |
| CL-04 조건별 문구 + 공통 경고 | `guide_variant_translation` (variant_key = 체류 목적 / WARNING) |
| 필수 11 · 안내 1 · 선택 3 | `guide.item_type` — 진행률은 REQUIRED 만 |

## 등록된 항목 (마이그레이션에서 ko 제목·근거 등록)

| code | 구분 | 제목 | 표시 조건 (코드) | 기한 (코드) |
|---|---|---|---|---|
| CL-01 | 필수 | 외국인등록 | userType=NEW AND NOT F4 | entryDate + 90일 |
| CL-02 | 필수 | 국내거소신고 | userType=NEW AND F4 | entryDate + 90일 |
| CL-04 | 필수 | 건강보험 가입 확인 | 항상 | 없음 (조건별 문구) |
| CL-05 | 필수 | 체류기간 연장 | stayExpiry 있음 | 시작 stayExpiry − 4개월 / 마감 stayExpiry |
| CL-06 | 필수 | 아르바이트 전 시간제 취업 허가 | purpose=STUDY | 없음 ("일하기 전") |
| CL-07 | 필수 | 직장을 바꿀 때 절차 알아두기 | purpose=WORK | 없음 ("바꿀 때") |
| CL-08 | 필수 | 가족센터 프로그램 보기 | purpose=FAMILY | 없음 ("언제든") |
| CL-09 | 필수 | 체류·취업 업종 상담 (☎1345) | purpose=OTHER | 없음 ("언제든") |
| CL-10 | 필수 | 자동차세 내기 | CAR | 매년 6/16~6/30, 12/16~12/31 |
| CL-11 | 필수 | 재산세 내기 | HOUSE | 매년 7/16~7/31, 9/16~9/30 |
| CL-12 | 필수 | 아이 학교 입학·전학 안내 | KIDS | 없음 ("3월 입학") |
| CL-13 | 안내 | 이사하면 거소 이전 신고 | userType=LIVING AND F4 | 없음 ("이사 시") |
| CL-20~22 | 선택 | 휴대폰 개통 / 은행 계좌 / 분리배출 요일 | userType=NEW | 없음 |
| LC-01-1~5 | 이사 | 체류지 변경(NOT F4, +15) · 거소 이전(F4, +14) · 자동차 주소(CAR, +30) · 자녀 전학(KIDS) · 쓰레기 배출 요일 | | |
| LC-02-1~4 | 이직 | 고용센터 사업장 변경(WORK) · 근무처 변경 신고(WORK, +15) · 시간제 취업 허가(STUDY) · 건강보험 직장 변경 | | |
| LC-03-1 | 입학 | 희망 학교에 입학 신청 | | |
| LC-04-1~2 | 차 구입 | 중고차 이전등록(+15) · 자동차보험 가입 | | |
| LC-05-1 | 출생 | 아기 체류자격 부여 신청 (+90) | | |
| LC-06-1 | 가족 입국 | 가족 외국인등록 (+90) | | |
| LC-07-1 | 출국 | 재입국허가 확인 (출국 전) | | |
| LC-08-1 | 이름·여권 변경 | 외국인등록사항 변경 신고 (+15) | | |

생활 변화 할 일 코드(`LC-01-1` 형식)는 규칙표에 없어서 백엔드에서 정했다. 규칙표의 변화 ID(LC-01)는 `guide.life_change_code` 와 `member_life_change.life_change_code` 에 그대로 쓴다.
en / zh / vi 문구와 장소·준비물·과태료 등 상세는 관리자 화면(또는 후속 마이그레이션)에서 채운다.

## 11/13 요구사항 수정으로 바뀐 것

| 요구사항 | DB 변경 |
|---|---|
| M-01·M-02 아이디 = 이메일 + 가입 인증 코드 | `app_user.login_id` 삭제, `email` NOT NULL + UNIQUE. `email_verified_at` 유지 — 새 회원은 가입 시 기록 (인증 코드는 Redis에만 보관) |
| M-11 비밀번호 재설정, NTF-05 이메일 알림, NTF-09 이메일 인증 | **유지** — 인증된 가입 이메일에만 발송. `notification.email_*` 유지 |
| ① 비회원 이용 없음 (CHK-9, CB-03, COM-01) | `chat_conversation.guest_session_id` 삭제 + `user_id` NOT NULL, `content_report.guest_session_id` 삭제, `app_user.*_updated_at`(기기값 병합용) 삭제 |
| CHK-8 입국 날짜만, 이사는 생활 변화 | `arrival_date` → `entry_date`, `move_date` 삭제 (LC-01 로 이동) |
| 이전 온보딩 정의서 대체 | `report_type`, `employment_status`, `region_code` 삭제 |

기존 회원 중 이메일이 없거나 겹치는 계정은 `{기존 login_id}@legacy.siot.invalid` 로 바뀌고 미인증 상태가 된다. 기존에 인증한 알림 이메일은 그대로 로그인 ID가 된다. 이전 온보딩 값(지역·이사일 등)은 새 규칙에 대응하는 값이 없어 버린다.
