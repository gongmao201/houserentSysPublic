-- =====================================================================
-- V2 · 存量数据规范化
-- 解决 5 个会污染业务逻辑的问题：
--   1) 金额 DOUBLE(元) → 新增 *_fen BIGINT（分），老字段只读保留
--   2) 租约时间 VARCHAR(50) → 新增 start_date / end_date DATE
--   3) 租约没有状态 → 新增 status + actual_end_date（评价窗口的触发器）
--   4) 单角色 sys_user.role → 新增 user_role 关联表（一人可多角色）
--   5) 密码 MD5 → 新增 pwd_algo，支持双哈希兼容与静默升级
-- 原则：只加不删、只增不改；老字段全部保留，保证 14 个老 Controller 不改代码也能跑
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 2.1 用户：密码算法标记 + 实名状态（房东侧筛选租客的信号之一）
-- ---------------------------------------------------------------------
ALTER TABLE sys_user
    ADD COLUMN pwd_algo          VARCHAR(16) NOT NULL DEFAULT 'md5' COMMENT '密码算法 md5/bcrypt' AFTER password,
    ADD COLUMN real_name_status  TINYINT     NOT NULL DEFAULT 0     COMMENT '实名状态 0未认证 1已认证 2审核中' AFTER mobile;

-- 多角色：租客也可能同时是房东（二房东场景）
CREATE TABLE IF NOT EXISTS user_role (
    id          VARCHAR(32) NOT NULL,
    user_id     VARCHAR(32) NOT NULL COMMENT '用户id',
    role        VARCHAR(16) NOT NULL COMMENT 'TENANT/LANDLORD/ADMIN',
    scope_id    VARCHAR(32) DEFAULT NULL COMMENT '作用域（预留：某小区/某门店）',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role),
    KEY idx_role (role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户多角色表';

-- 回填：把存量单值 role 拆到关联表（0租户 1房东 2超管）
INSERT IGNORE INTO user_role (id, user_id, role, create_time)
SELECT CONCAT('UR', LPAD(ROW_NUMBER() OVER (ORDER BY id), 8, '0')), id,
       CASE role WHEN 0 THEN 'TENANT' WHEN 1 THEN 'LANDLORD' ELSE 'ADMIN' END,
       NOW()
FROM sys_user
WHERE del_flag = 0;

-- ---------------------------------------------------------------------
-- 2.2 房源：金额分字段
-- ---------------------------------------------------------------------
ALTER TABLE hrent_house_resource
    ADD COLUMN money_fen BIGINT DEFAULT NULL COMMENT '租金(分/月)，与 money 同步' AFTER money;

UPDATE hrent_house_resource
SET money_fen = ROUND(money * 100)
WHERE money IS NOT NULL AND money_fen IS NULL;

-- 房源列表主查询索引：状态 + 区县 + 价格
ALTER TABLE hrent_house_resource
    ADD KEY idx_list (state, county, money_fen),
    ADD KEY idx_city_price (city, money_fen);

-- ---------------------------------------------------------------------
-- 2.3 租房申请：押金分字段
-- ---------------------------------------------------------------------
ALTER TABLE hrent_apply_rent
    ADD COLUMN deposit_fen BIGINT DEFAULT NULL COMMENT '押金(分)' AFTER deposit;

UPDATE hrent_apply_rent
SET deposit_fen = ROUND(deposit * 100)
WHERE deposit IS NOT NULL AND deposit_fen IS NULL;

-- ---------------------------------------------------------------------
-- 2.4 在租信息 → 租约（关键：状态机 + 实际退租日）
--     status: 0生效中 1已到期 2已退租 3已解约
--     评价窗口 = actual_end_date 之后 30 天内
-- ---------------------------------------------------------------------
ALTER TABLE hrent_house_renting
    ADD COLUMN money_fen       BIGINT      DEFAULT NULL COMMENT '租金(分/月)' AFTER money,
    ADD COLUMN start_date      DATE        DEFAULT NULL COMMENT '起租日' AFTER start_time,
    ADD COLUMN end_date        DATE        DEFAULT NULL COMMENT '合同到期日' AFTER end_time,
    ADD COLUMN status          TINYINT     NOT NULL DEFAULT 0 COMMENT '0生效中 1已到期 2已退租 3已解约' AFTER end_date,
    ADD COLUMN actual_end_date DATE        DEFAULT NULL COMMENT '实际退租日（评价窗口起点）' AFTER status,
    ADD COLUMN version         INT         NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER actual_end_date;

UPDATE hrent_house_renting
SET money_fen = ROUND(money * 100)
WHERE money IS NOT NULL AND money_fen IS NULL;

-- 日期回填：兼容 'yyyy-MM-dd HH:mm:ss' / 'yyyy-MM-dd' / 'yyyy/MM/dd' 三种写法
UPDATE hrent_house_renting
SET start_date = DATE(COALESCE(
        STR_TO_DATE(start_time, '%Y-%m-%d %H:%i:%s'),
        STR_TO_DATE(start_time, '%Y-%m-%d'),
        STR_TO_DATE(start_time, '%Y/%m/%d')))
WHERE start_time IS NOT NULL AND start_date IS NULL;

UPDATE hrent_house_renting
SET end_date = DATE(COALESCE(
        STR_TO_DATE(end_time, '%Y-%m-%d %H:%i:%s'),
        STR_TO_DATE(end_time, '%Y-%m-%d'),
        STR_TO_DATE(end_time, '%Y/%m/%d')))
WHERE end_time IS NOT NULL AND end_date IS NULL;

-- 状态回填：已过期 → 1，其余保持 0
UPDATE hrent_house_renting
SET status = 1
WHERE status = 0 AND end_date IS NOT NULL AND end_date < CURDATE();

ALTER TABLE hrent_house_renting
    ADD KEY idx_tenant_status (tenant_id, status),
    ADD KEY idx_house_status (house_id, status),
    ADD KEY idx_actual_end (actual_end_date);

-- 租约流转日志（与报修日志同构，便于统一时间线组件）
CREATE TABLE IF NOT EXISTS lease_log (
    id          VARCHAR(32) NOT NULL,
    lease_id    VARCHAR(32) NOT NULL COMMENT '租约id',
    from_status TINYINT     DEFAULT NULL COMMENT '原状态',
    to_status   TINYINT     NOT NULL COMMENT '新状态',
    operator_id VARCHAR(32) DEFAULT NULL COMMENT '操作人',
    remark      VARCHAR(255) DEFAULT NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_lease (lease_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租约流转日志';

-- ---------------------------------------------------------------------
-- 2.5 迁移校验（失败即中断迁移，不允许静默通过）
--     实现方式：往带 CHECK 约束的表里插"异常行数"，>0 直接报错。
--     不用存储过程 —— Flyway 对 DELIMITER 支持不稳定，会解析失败。
--     注：CHECK 约束需 MySQL 8.0.16+（本项目目标 8.0，满足）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS _v2_assert;
CREATE TABLE _v2_assert (
    id      INT PRIMARY KEY AUTO_INCREMENT,
    rule    VARCHAR(64) NOT NULL,
    bad_rows BIGINT     NOT NULL,
    CONSTRAINT ck_no_bad_rows CHECK (bad_rows = 0)
) ENGINE = InnoDB;

-- 1) 金额未回填
INSERT INTO _v2_assert (rule, bad_rows)
SELECT 'money_fen 未回填', COUNT(*) FROM hrent_house_resource
WHERE money IS NOT NULL AND money_fen IS NULL;

-- 2) 日期无法解析（有源值但目标为空）
INSERT INTO _v2_assert (rule, bad_rows)
SELECT 'end_time 无法解析', COUNT(*) FROM hrent_house_renting
WHERE end_time IS NOT NULL AND end_date IS NULL;

-- 3) 角色未回填
INSERT INTO _v2_assert (rule, bad_rows)
SELECT 'user_role 未回填', COUNT(*)
FROM sys_user u LEFT JOIN user_role r ON r.user_id = u.id
WHERE u.del_flag = 0 AND r.id IS NULL;

-- 4) 押金未回填
INSERT INTO _v2_assert (rule, bad_rows)
SELECT 'deposit_fen 未回填', COUNT(*) FROM hrent_apply_rent
WHERE deposit IS NOT NULL AND deposit_fen IS NULL;

DROP TABLE _v2_assert;
