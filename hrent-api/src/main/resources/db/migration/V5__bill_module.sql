-- =====================================================================
-- V5 · 账单模块（扩展存量 hrent_rent，不新建 bill 表）
-- 为什么扩表：hrent_rent 已经是"按租约 + 应缴日期 + 实缴日期 + 状态"的账单雏形，
--             新建三张表会让"在租管理"页与"账单"页对不上账。
-- 兼容策略：老字段 state(0未到/1已到/2已交) 保留不动，新增 bill_status 表达新状态机，
--           老代码继续读 state，新代码读 bill_status。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 5.1 账单主表扩列
--     bill_status: UNPAID 待缴 / MARKED 租客已标记 / PAID 房东已确认 / OVERDUE 逾期
-- ---------------------------------------------------------------------
ALTER TABLE hrent_rent
    ADD COLUMN period       VARCHAR(7)  DEFAULT NULL COMMENT '账期 YYYY-MM' AFTER renting_id,
    ADD COLUMN amount_fen   BIGINT      DEFAULT NULL COMMENT '本期应收(分)' AFTER period,
    ADD COLUMN paid_fen     BIGINT      NOT NULL DEFAULT 0 COMMENT '已收(分)' AFTER amount_fen,
    ADD COLUMN bill_status  VARCHAR(16) NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/MARKED/PAID/OVERDUE' AFTER state,
    ADD COLUMN channel      VARCHAR(16) DEFAULT NULL COMMENT 'offline_wechat/offline_alipay/offline_cash' AFTER bill_status,
    ADD COLUMN voucher_pic  VARCHAR(300) DEFAULT NULL COMMENT '转账凭证图片' AFTER channel,
    ADD COLUMN confirm_at   DATETIME    DEFAULT NULL COMMENT '房东确认到账时间' AFTER voucher_pic,
    ADD COLUMN version      INT         NOT NULL DEFAULT 0 COMMENT '乐观锁';

-- 账期回填：从应缴日期取 YYYY-MM
UPDATE hrent_rent
SET period = DATE_FORMAT(payable_time, '%Y-%m')
WHERE payable_time IS NOT NULL AND period IS NULL;

-- 金额回填：从租约取月租（按分）
UPDATE hrent_rent r
JOIN hrent_house_renting g ON g.id = r.renting_id
SET r.amount_fen = COALESCE(r.amount_fen, g.money_fen),
    r.paid_fen   = CASE WHEN r.state = 2 THEN COALESCE(g.money_fen, 0) ELSE 0 END
WHERE r.amount_fen IS NULL;

-- 状态回填：老 state=2 视为已缴
UPDATE hrent_rent
SET bill_status = 'PAID', confirm_at = COALESCE(pay_time, update_time, create_time)
WHERE state = 2 AND bill_status = 'UNPAID';

-- 逾期回填：应缴日已过且未缴
UPDATE hrent_rent
SET bill_status = 'OVERDUE'
WHERE bill_status = 'UNPAID' AND payable_time IS NOT NULL AND payable_time < NOW();

-- 同一租约同一账期只能有一张账单（period 为 NULL 的老数据不受影响，MySQL 允许多个 NULL）
ALTER TABLE hrent_rent
    ADD UNIQUE KEY uk_renting_period (renting_id, period),
    ADD KEY idx_tenant_status (tenant_id, bill_status, payable_time),
    ADD KEY idx_status_due (bill_status, payable_time);

-- ---------------------------------------------------------------------
-- 5.2 账单明细（唯一新增表）
--     约束：SUM(items.amount_fen) 必须等于主表 amount_fen，由服务层校验
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bill_item (
    id         VARCHAR(32) NOT NULL,
    rent_id    VARCHAR(32) NOT NULL COMMENT '账单id（hrent_rent.id）',
    item_type  VARCHAR(16) NOT NULL COMMENT 'RENT/WATER/ELECTRIC/PROPERTY/OTHER',
    amount_fen BIGINT      NOT NULL COMMENT '金额(分)，可为 0',
    remark     VARCHAR(200) DEFAULT NULL COMMENT '如"150 度"、"12 吨"',
    sort       INT          NOT NULL DEFAULT 0,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_rent (rent_id, sort),
    CONSTRAINT ck_bill_item_type CHECK (item_type IN ('RENT','WATER','ELECTRIC','PROPERTY','OTHER')),
    CONSTRAINT ck_bill_item_amount CHECK (amount_fen >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '账单明细';

-- ---------------------------------------------------------------------
-- 5.3 校验：已生成明细的账单，明细之和必须等于应收
--     用 CHECK 表让 Flyway 在数据不一致时中断，而不是"看起来对"
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS _v5_assert;
CREATE TABLE _v5_assert (
    id       INT PRIMARY KEY AUTO_INCREMENT,
    rule     VARCHAR(64) NOT NULL,
    bad_rows BIGINT      NOT NULL,
    CONSTRAINT ck_v5_no_bad CHECK (bad_rows = 0)
) ENGINE = InnoDB;

INSERT INTO _v5_assert (rule, bad_rows)
SELECT '账单明细与应收不等', COUNT(*)
FROM (
    SELECT r.id, r.amount_fen, COALESCE(SUM(i.amount_fen), 0) AS item_sum
    FROM hrent_rent r
    JOIN bill_item i ON i.rent_id = r.id
    WHERE r.amount_fen IS NOT NULL
    GROUP BY r.id, r.amount_fen
) t
WHERE t.item_sum <> t.amount_fen;

DROP TABLE _v5_assert;
