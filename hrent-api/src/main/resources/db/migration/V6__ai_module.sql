-- =====================================================================
-- V6 · AI 模块（本期只建表，不写入）
-- 说明：00 文档 3.1.1 已定"轻量 RAG 移二期"，但表先建好，二期只补代码，
--       避免再开一次迁移。本期代码不得读写这两张表（红线）。
--       ai_feedback 例外：本期就要用，用于评测集校准（点击/曝光埋点）。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 6.1 房源向量（二期启用）
--     本期不生成 embedding；表存在但为空，代码里不得引用
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS house_embedding (
    house_id     VARCHAR(32)  NOT NULL,
    model        VARCHAR(64)  NOT NULL DEFAULT 'nomic-embed-text',
    dim          INT          NOT NULL DEFAULT 768,
    vec          VARBINARY(4096) NOT NULL COMMENT 'float32 数组序列化',
    content_hash CHAR(32)     NOT NULL COMMENT '描述哈希，变了才重算',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (house_id),
    KEY idx_hash (content_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '房源向量(二期)';

-- ---------------------------------------------------------------------
-- 6.2 AI 反馈埋点（本期就用）
--     action: IMPRESSION 曝光 / CLICK 点击 / APPLY 申请
--     用途：算 Top5 点击率、校准 50 条评测集
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_feedback (
    id         VARCHAR(32) NOT NULL,
    user_id    VARCHAR(32) DEFAULT NULL,
    query      VARCHAR(500) DEFAULT NULL COMMENT '原始输入（脱敏后）',
    house_id   VARCHAR(32) NOT NULL,
    action     VARCHAR(16) NOT NULL COMMENT 'IMPRESSION/CLICK/APPLY',
    position   INT         DEFAULT NULL COMMENT '推荐位次 1~10',
    mode       VARCHAR(8)  DEFAULT NULL COMMENT 'rule/llm',
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_house_action (house_id, action, create_time),
    KEY idx_time (create_time),
    CONSTRAINT ck_ai_action CHECK (action IN ('IMPRESSION','CLICK','APPLY')),
    CONSTRAINT ck_ai_mode   CHECK (mode IS NULL OR mode IN ('rule','llm'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI 推荐反馈埋点';
