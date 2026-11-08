-- =====================================================================
-- 초기 스키마 (Notion "데이터베이스 ERD" 기준, MySQL 문법 -> PostgreSQL 변환)
-- ERD 대비 추가 사항:
--   * ERD에 누락된 FK 보완
--   * M-07(탈퇴 시 즉시 물리 삭제): 회원 소유 데이터 ON DELETE CASCADE,
--     작성자·처리자 등 이력 참조 ON DELETE SET NULL
--   * UNIQUE: app_user.login_id, event(external_source, external_id)
--   * document_chunk.embedding HNSW 인덱스 (cosine)
-- =====================================================================

-- ---------------------------------------------------------------------
-- member
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    user_id           BIGSERIAL    PRIMARY KEY,
    login_id          VARCHAR(20)  NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NULL,
    display_name      VARCHAR(100) NOT NULL,
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER',
    language          VARCHAR(10)  NOT NULL DEFAULT 'ko',
    user_type         VARCHAR(30)  NULL,
    report_type       VARCHAR(30)  NULL,
    employment_status VARCHAR(30)  NULL,
    region_code       VARCHAR(20)  NULL,
    arrival_date      DATE         NULL,
    move_date         DATE         NULL,
    stay_expiry       DATE         NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_login_id UNIQUE (login_id)
);

-- ---------------------------------------------------------------------
-- auth (인증 방식 확정 전까지 ERD대로 유지)
-- ---------------------------------------------------------------------
CREATE TABLE refresh_token (
    token_id   BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked_at TIMESTAMP    NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- checklist
-- ---------------------------------------------------------------------
CREATE TABLE guide (
    guide_id        BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL,
    kind            VARCHAR(30)  NOT NULL,
    category        VARCHAR(100) NULL,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    counts_progress BOOLEAN      NOT NULL DEFAULT FALSE,
    source_url      TEXT         NULL,
    contact         VARCHAR(255) NULL,
    created_by      BIGINT       NULL,
    updated_by      BIGINT       NULL,
    deleted_at      TIMESTAMP    NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guide_created_by FOREIGN KEY (created_by) REFERENCES app_user (user_id) ON DELETE SET NULL,
    CONSTRAINT fk_guide_updated_by FOREIGN KEY (updated_by) REFERENCES app_user (user_id) ON DELETE SET NULL
);

CREATE TABLE guide_translation (
    language VARCHAR(10)  NOT NULL,
    guide_id BIGINT       NOT NULL,
    title    VARCHAR(255) NOT NULL,
    summary  TEXT         NULL,
    content  TEXT         NOT NULL,
    CONSTRAINT pk_guide_translation PRIMARY KEY (language, guide_id),
    CONSTRAINT fk_guide_translation_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id)
);

CREATE TABLE checklist_progress (
    user_id      BIGINT    NOT NULL,
    guide_id     BIGINT    NOT NULL,
    is_selected  BOOLEAN   NOT NULL DEFAULT TRUE,
    is_completed BOOLEAN   NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_checklist_progress PRIMARY KEY (user_id, guide_id),
    CONSTRAINT fk_checklist_progress_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_checklist_progress_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id)
);

-- ---------------------------------------------------------------------
-- schedule
-- ---------------------------------------------------------------------
CREATE TABLE calendar_schedule (
    schedule_id        BIGSERIAL   PRIMARY KEY,
    guide_id           BIGINT      NULL,
    kind               VARCHAR(30) NOT NULL,
    starts_on          DATE        NOT NULL,
    ends_on            DATE        NULL,
    target_description TEXT        NULL,
    source_url         TEXT        NULL,
    is_active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_by         BIGINT      NULL,
    updated_by         BIGINT      NULL,
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_calendar_schedule_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id),
    CONSTRAINT fk_calendar_schedule_created_by FOREIGN KEY (created_by) REFERENCES app_user (user_id) ON DELETE SET NULL,
    CONSTRAINT fk_calendar_schedule_updated_by FOREIGN KEY (updated_by) REFERENCES app_user (user_id) ON DELETE SET NULL
);

-- ---------------------------------------------------------------------
-- event
-- ---------------------------------------------------------------------
CREATE TABLE event (
    event_id               BIGSERIAL    PRIMARY KEY,
    external_source        VARCHAR(100) NULL,
    external_id            VARCHAR(255) NULL,
    region_code            VARCHAR(20)  NULL,
    starts_on              DATE         NULL,
    ends_on                DATE         NULL,
    starts_at              TIME         NULL,
    ends_at                TIME         NULL,
    application_deadline   TIMESTAMP    NULL,
    time_is_unknown        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_multicultural       BOOLEAN      NOT NULL DEFAULT FALSE,
    source_url             TEXT         NULL,
    price_info             TEXT         NULL,
    application_url        TEXT         NULL,
    starts_on_locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    ends_on_locked         BOOLEAN      NOT NULL DEFAULT FALSE,
    starts_at_locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    ends_at_locked         BOOLEAN      NOT NULL DEFAULT FALSE,
    deadline_locked        BOOLEAN      NOT NULL DEFAULT FALSE,
    price_info_locked      BOOLEAN      NOT NULL DEFAULT FALSE,
    application_url_locked BOOLEAN      NOT NULL DEFAULT FALSE,
    revision               INTEGER      NOT NULL DEFAULT 1,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_by             BIGINT       NULL,
    updated_by             BIGINT       NULL,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 수집 중복 방지 (NULL 값끼리는 중복 허용)
    CONSTRAINT uk_event_external UNIQUE (external_source, external_id),
    CONSTRAINT fk_event_created_by FOREIGN KEY (created_by) REFERENCES app_user (user_id) ON DELETE SET NULL,
    CONSTRAINT fk_event_updated_by FOREIGN KEY (updated_by) REFERENCES app_user (user_id) ON DELETE SET NULL
);

CREATE TABLE event_translation (
    language           VARCHAR(10)  NOT NULL,
    event_id           BIGINT       NOT NULL,
    title              VARCHAR(255) NOT NULL,
    description        TEXT         NULL,
    venue              VARCHAR(255) NULL,
    title_locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    description_locked BOOLEAN      NOT NULL DEFAULT FALSE,
    venue_locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_event_translation PRIMARY KEY (language, event_id),
    CONSTRAINT fk_event_translation_event FOREIGN KEY (event_id) REFERENCES event (event_id)
);

CREATE TABLE event_subscription (
    user_id    BIGINT    NOT NULL,
    event_id   BIGINT    NOT NULL,
    enabled    BOOLEAN   NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_event_subscription PRIMARY KEY (user_id, event_id),
    CONSTRAINT fk_event_subscription_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_event_subscription_event FOREIGN KEY (event_id) REFERENCES event (event_id)
);

-- ---------------------------------------------------------------------
-- rag: 문서 / 임베딩
-- ---------------------------------------------------------------------
CREATE TABLE document (
    document_id  BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    source_url   TEXT         NULL,
    published_on DATE         NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE document_version (
    document_version_id BIGSERIAL    PRIMARY KEY,
    document_id         BIGINT       NOT NULL,
    version_no          INTEGER      NOT NULL,
    original_filename   VARCHAR(255) NULL,
    file_storage_key    TEXT         NOT NULL,
    checksum            VARCHAR(255) NULL,
    uploaded_by         BIGINT       NULL,
    change_type         VARCHAR(30)  NULL,
    change_summary      TEXT         NULL,
    processing_status   VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    processing_error    TEXT         NULL,
    processed_at        TIMESTAMP    NULL,
    is_current          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_version_document FOREIGN KEY (document_id) REFERENCES document (document_id),
    CONSTRAINT fk_document_version_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES app_user (user_id) ON DELETE SET NULL
);

CREATE TABLE document_chunk (
    chunk_id            BIGSERIAL    PRIMARY KEY,
    document_version_id BIGINT       NOT NULL,
    chunk_no            INTEGER      NOT NULL,
    page_start          INTEGER      NULL,
    page_end            INTEGER      NULL,
    content             TEXT         NOT NULL,
    embedding_model     VARCHAR(100) NOT NULL DEFAULT 'gemini-embedding-001',
    embedding           VECTOR(768)  NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_chunk_version FOREIGN KEY (document_version_id) REFERENCES document_version (document_version_id)
);

-- 코사인 유사도 기반 근사 최근접 검색
CREATE INDEX idx_document_chunk_embedding
    ON document_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE guide_document_reference (
    reference_id        BIGSERIAL   PRIMARY KEY,
    guide_id            BIGINT      NOT NULL,
    document_version_id BIGINT      NOT NULL,
    page_start          INTEGER     NULL,
    page_end            INTEGER     NULL,
    reference_type      VARCHAR(30) NULL,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guide_document_reference_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id),
    CONSTRAINT fk_guide_document_reference_version FOREIGN KEY (document_version_id) REFERENCES document_version (document_version_id)
);

-- ---------------------------------------------------------------------
-- rag: 챗봇
-- ---------------------------------------------------------------------
CREATE TABLE chat_conversation (
    conversation_id  BIGSERIAL   PRIMARY KEY,
    user_id          BIGINT      NULL,
    guest_session_id UUID        NULL,
    language         VARCHAR(10) NOT NULL DEFAULT 'ko',
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_conversation_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE
);

CREATE TABLE chat_message (
    message_id       BIGSERIAL   PRIMARY KEY,
    conversation_id  BIGINT      NOT NULL,
    role             VARCHAR(20) NOT NULL,
    content          TEXT        NOT NULL,
    answer_status    VARCHAR(30) NULL,
    is_helpful       BOOLEAN     NULL,
    feedback_comment TEXT        NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversation (conversation_id) ON DELETE CASCADE
);

CREATE TABLE chat_message_source (
    source_id       BIGSERIAL        PRIMARY KEY,
    message_id      BIGINT           NOT NULL,
    chunk_id        BIGINT           NOT NULL,
    relevance_score DOUBLE PRECISION NULL,
    source_order    INTEGER          NOT NULL,
    created_at      TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_source_message FOREIGN KEY (message_id) REFERENCES chat_message (message_id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_source_chunk FOREIGN KEY (chunk_id) REFERENCES document_chunk (chunk_id)
);

-- ---------------------------------------------------------------------
-- notification
-- ---------------------------------------------------------------------
CREATE TABLE notification_preference (
    kind       VARCHAR(30) NOT NULL,
    user_id    BIGINT      NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    lead_days  INTEGER     NOT NULL DEFAULT 0,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notification_preference PRIMARY KEY (kind, user_id),
    CONSTRAINT fk_notification_preference_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE
);

CREATE TABLE notification (
    notification_id     BIGSERIAL    PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    kind                VARCHAR(30)  NOT NULL,
    event_id            BIGINT       NULL,
    guide_id            BIGINT       NULL,
    schedule_id         BIGINT       NULL,
    document_version_id BIGINT       NULL,
    source_key          VARCHAR(255) NULL,
    source_revision     INTEGER      NULL,
    scheduled_at        TIMESTAMP    NOT NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    attempts            INTEGER      NOT NULL DEFAULT 0,
    locked_at           TIMESTAMP    NULL,
    last_error          TEXT         NULL,
    locale              VARCHAR(10)  NOT NULL DEFAULT 'ko',
    title               VARCHAR(255) NOT NULL,
    body                TEXT         NOT NULL,
    delivered_at        TIMESTAMP    NULL,
    read_at             TIMESTAMP    NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_event FOREIGN KEY (event_id) REFERENCES event (event_id),
    CONSTRAINT fk_notification_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id),
    CONSTRAINT fk_notification_schedule FOREIGN KEY (schedule_id) REFERENCES calendar_schedule (schedule_id),
    CONSTRAINT fk_notification_document_version FOREIGN KEY (document_version_id) REFERENCES document_version (document_version_id)
);

-- ---------------------------------------------------------------------
-- report
-- ---------------------------------------------------------------------
CREATE TABLE content_report (
    report_id             BIGSERIAL    PRIMARY KEY,
    reporter_user_id      BIGINT       NULL,
    guest_session_id      UUID         NULL,
    event_id              BIGINT       NULL,
    guide_id              BIGINT       NULL,
    target_title_snapshot VARCHAR(255) NULL,
    quoted_text           TEXT         NULL,
    reason                VARCHAR(50)  NOT NULL,
    description           TEXT         NULL,
    status                VARCHAR(30)  NOT NULL DEFAULT 'RECEIVED',
    admin_memo            TEXT         NULL,
    processed_by          BIGINT       NULL,
    processed_at          TIMESTAMP    NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_report_reporter FOREIGN KEY (reporter_user_id) REFERENCES app_user (user_id) ON DELETE SET NULL,
    CONSTRAINT fk_content_report_event FOREIGN KEY (event_id) REFERENCES event (event_id),
    CONSTRAINT fk_content_report_guide FOREIGN KEY (guide_id) REFERENCES guide (guide_id),
    CONSTRAINT fk_content_report_processed_by FOREIGN KEY (processed_by) REFERENCES app_user (user_id) ON DELETE SET NULL
);
