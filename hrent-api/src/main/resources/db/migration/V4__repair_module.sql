-- =====================================================================
-- V4 · 报修模块（工单 + 图片 + 流转日志）
-- 状态机：CREATED → ACCEPTED → ON_SITE → COMPLETED → ACCEPTED_BY_TENANT
--         任意非终态 → CANCELLED
-- 三个设计要点：
--   1) version 乐观锁：两个房东同时接单只有一个成功（返回 409）
--   2) idem_key：客户端带 Idempotency-Key，24h 内重复提交返回同一单
--   3) repair_log 存流转轨迹，前端时间线直接读它，不用拼状态
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 4.1 报修工单
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS repair_order (
    id           VARCHAR(32)  NOT NULL,
    house_id     VARCHAR(32)  NOT NULL COMMENT '房源id',
    lease_id     VARCHAR(32)  DEFAULT NULL COMMENT '租约id（在租时必填）',
    tenant_id    VARCHAR(32)  NOT NULL COMMENT '报修人（租客）',
    landlord_id  VARCHAR(32)  DEFAULT NULL COMMENT '房东id（冗余，便于按房东查）',
    type         VARCHAR(16)  NOT NULL COMMENT 'WATER/ELECTRIC/DOOR_WINDOW/APPLIANCE/OTHER',
    description  VARCHAR(1000) NOT NULL COMMENT '问题描述',
    expect_time  DATETIME     DEFAULT NULL COMMENT '期望上门时间',
    status       VARCHAR(24)  NOT NULL DEFAULT 'CREATED' COMMENT '见文件头状态机',
    accepted_by  VARCHAR(32)  DEFAULT NULL COMMENT '接单人（房东或师傅）',
    accepted_at  DATETIME     DEFAULT NULL,
    arrived_at   DATETIME     DEFAULT NULL,
    completed_at DATETIME     DEFAULT NULL,
    confirmed_at DATETIME     DEFAULT NULL,
    cancel_reason VARCHAR(255) DEFAULT NULL,
    tenant_score TINYINT      DEFAULT NULL COMMENT '租客对维修的评分 1~5',
    tenant_comment VARCHAR(500) DEFAULT NULL,
    version      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    idem_key     VARCHAR(64)  DEFAULT NULL COMMENT '幂等键（Idempotency-Key）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    del_flag     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem (idem_key),
    KEY idx_tenant (tenant_id, status, create_time),
    KEY idx_landlord (landlord_id, status, create_time),
    KEY idx_house (house_id, status),
    KEY idx_status (status, create_time),
    CONSTRAINT ck_repair_status CHECK (status IN ('CREATED','ACCEPTED','ON_SITE','COMPLETED','ACCEPTED_BY_TENANT','CANCELLED')),
    CONSTRAINT ck_repair_type   CHECK (type IN ('WATER','ELECTRIC','DOOR_WINDOW','APPLIANCE','OTHER')),
    CONSTRAINT ck_repair_score  CHECK (tenant_score IS NULL OR (tenant_score BETWEEN 1 AND 5))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '报修工单';

-- ---------------------------------------------------------------------
-- 4.2 工单图片（提交时 1~3 张，完成后可再传维修后照片）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS repair_image (
    id         VARCHAR(32) NOT NULL,
    repair_id  VARCHAR(32) NOT NULL,
    url        VARCHAR(300) NOT NULL COMMENT '相对路径 /uploads/...',
    stage      VARCHAR(16) NOT NULL DEFAULT 'SUBMIT' COMMENT 'SUBMIT/COMPLETE',
    sort       INT         NOT NULL DEFAULT 0,
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_repair (repair_id, stage, sort),
    CONSTRAINT ck_repair_img_stage CHECK (stage IN ('SUBMIT','COMPLETE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工单图片';

-- ---------------------------------------------------------------------
-- 4.3 流转日志（时间线数据源）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS repair_log (
    id          VARCHAR(32) NOT NULL,
    repair_id   VARCHAR(32) NOT NULL,
    from_status VARCHAR(24) DEFAULT NULL,
    to_status   VARCHAR(24) NOT NULL,
    operator_id VARCHAR(32) DEFAULT NULL,
    operator_role VARCHAR(16) DEFAULT NULL COMMENT 'TENANT/LANDLORD/ADMIN',
    remark      VARCHAR(255) DEFAULT NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_repair (repair_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工单流转日志';
