ALTER TABLE tb_user ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tb_user ADD CONSTRAINT ck_user_session_version_nonnegative CHECK (session_version >= 0);
