-- =====================================================================
-- V3 · 评分模块（平台核验 + 用户评价 + 申诉 + 汇总）
-- 对应 00 文档 3.2：房源双维评分 + 房东服务评分（一期不做租客评分）
-- 三个设计要点：
--   1) "官方分"落到 house_audit（上架一次性核验 8 项），不做持续巡检
--   2) 评价必须绑定租约（lease_id），这是"租约结束后 30 天内可评"的物理约束
--   3) rating_summary 存算好的分，列表排序直接读它，不在列表页跑聚合
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 3.1 房源核验单（替代"官方巡检"）
--     8 项权重之和 = 1.00，得分 = Σ(命中权重) × 5，落在 0~5
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS house_audit (
    id                VARCHAR(32) NOT NULL,
    house_id          VARCHAR(32) NOT NULL COMMENT '房源id',
    auditor_id        VARCHAR(32) NOT NULL COMMENT '核验人（管理员）',
    photo_real        TINYINT     NOT NULL DEFAULT 0 COMMENT '照片真实 0.20',
    address_verified  TINYINT     NOT NULL DEFAULT 0 COMMENT '地址可核验 0.20',
    facility_match    TINYINT     NOT NULL DEFAULT 0 COMMENT '设施与描述一致 0.15',
    price_reasonable  TINYINT     NOT NULL DEFAULT 0 COMMENT '价格合理 0.15',
    contract_uploaded TINYINT     NOT NULL DEFAULT 0 COMMENT '合同模板已上传 0.10',
    safety_checked    TINYINT     NOT NULL DEFAULT 0 COMMENT '消防与安全 0.10',
    deposit_rule_clear TINYINT    NOT NULL DEFAULT 0 COMMENT '押付规则明确 0.05',
    no_complaint      TINYINT     NOT NULL DEFAULT 0 COMMENT '无历史投诉 0.05',
    score             DECIMAL(3,2) NOT NULL COMMENT '核验分 0.00~5.00',
    conclusion        VARCHAR(500) DEFAULT NULL COMMENT '结论/备注',
    create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_house_time (house_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '房源核验单';

-- ---------------------------------------------------------------------
-- 3.2 评价流水
--     target_type: HOUSE / LANDLORD（一期不做 TENANT）
--     status: PUBLISHED / APPEALING / HIDDEN
--     唯一约束：同一租约 + 同一评价人 + 同一对象，只能一条
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS review (
    id          VARCHAR(32)  NOT NULL,
    lease_id    VARCHAR(32)  NOT NULL COMMENT '关联租约（评价资格来源）',
    reviewer_id VARCHAR(32)  NOT NULL COMMENT '评价人',
    target_type VARCHAR(16)  NOT NULL COMMENT 'HOUSE/LANDLORD',
    target_id   VARCHAR(32)  NOT NULL COMMENT '房源id 或 房东id',
    score       DECIMAL(2,1) NOT NULL COMMENT '总分 1.0~5.0',
    dimensions  JSON         DEFAULT NULL COMMENT '维度分，见文档 3.2',
    content     VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
    images      JSON         DEFAULT NULL COMMENT '图片路径数组，最多 3 张',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/APPEALING/HIDDEN',
    reply       VARCHAR(100) DEFAULT NULL COMMENT '房东回复（每条一次）',
    reply_at    DATETIME     DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    del_flag    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviewer_target (reviewer_id, target_type, target_id, lease_id),
    KEY idx_target (target_type, target_id, status, create_time),
    KEY idx_lease (lease_id),
    CONSTRAINT ck_review_score CHECK (score >= 1.0 AND score <= 5.0),
    CONSTRAINT ck_review_type  CHECK (target_type IN ('HOUSE','LANDLORD')),
    CONSTRAINT ck_review_status CHECK (status IN ('PUBLISHED','APPEALING','HIDDEN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评价流水';

-- ---------------------------------------------------------------------
-- 3.3 评价申诉（个保法第 24 条：对自然人的自动化决策需可申诉）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS review_appeal (
    id          VARCHAR(32)  NOT NULL,
    review_id   VARCHAR(32)  NOT NULL,
    applicant_id VARCHAR(32) NOT NULL COMMENT '申诉人（被评价方）',
    reason      VARCHAR(500) NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
    handled_by  VARCHAR(32)  DEFAULT NULL,
    handled_at  DATETIME     DEFAULT NULL,
    result      VARCHAR(500) DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_applicant (review_id, applicant_id),
    KEY idx_status (status, create_time),
    CONSTRAINT ck_appeal_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评价申诉';

-- ---------------------------------------------------------------------
-- 3.4 评分汇总（列表排序读这张表，不在列表页跑聚合）
--     display_score = 贝叶斯平滑后的用户分；official_score 为空时前端按 1.0 权重用 user_score
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rating_summary (
    target_type    VARCHAR(16)  NOT NULL COMMENT 'HOUSE/LANDLORD',
    target_id      VARCHAR(32)  NOT NULL,
    official_score DECIMAL(3,2) DEFAULT NULL COMMENT '核验分，无核验为 NULL',
    user_score     DECIMAL(3,2) DEFAULT NULL COMMENT '用户原始均分',
    display_score  DECIMAL(3,2) NOT NULL DEFAULT 4.00 COMMENT '贝叶斯平滑后展示分',
    review_count   INT          NOT NULL DEFAULT 0,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (target_type, target_id),
    KEY idx_display (target_type, display_score)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评分汇总';

-- ---------------------------------------------------------------------
-- 3.5 汇总刷新（幂等，可随时重跑；后端服务也应实现同一套公式）
--     贝叶斯：display = (C*m + Σs) / (C + n)，C=5, m=4.0
--     最终分由接口层组合：official 存在 → official*0.4 + display*0.6，否则 display*1.0
-- ---------------------------------------------------------------------
INSERT INTO rating_summary (target_type, target_id, official_score, user_score, display_score, review_count)
SELECT t.target_type, t.target_id, NULL,
       ROUND(AVG(t.score), 2),
       ROUND((5 * 4.0 + SUM(t.score)) / (5 + COUNT(*)), 2),
       COUNT(*)
FROM review t
WHERE t.del_flag = 0 AND t.status = 'PUBLISHED'
GROUP BY t.target_type, t.target_id
ON DUPLICATE KEY UPDATE
    user_score    = VALUES(user_score),
    display_score = VALUES(display_score),
    review_count  = VALUES(review_count),
    updated_at    = CURRENT_TIMESTAMP;

-- 把最新一条核验分写进汇总（房源）
INSERT INTO rating_summary (target_type, target_id, official_score, display_score, review_count)
SELECT 'HOUSE', a.house_id, a.score, 4.00, 0
FROM house_audit a
JOIN (SELECT house_id, MAX(create_time) AS mx FROM house_audit GROUP BY house_id) m
  ON m.house_id = a.house_id AND m.mx = a.create_time
ON DUPLICATE KEY UPDATE official_score = VALUES(official_score), updated_at = CURRENT_TIMESTAMP;
