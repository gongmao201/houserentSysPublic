-- =====================================================================
-- 回滚脚本 · 仅限演示/测试环境
--
-- ！！危险！！ 会 DROP 表、DROP 列，数据不可恢复。
-- 生产环境不要用这个脚本回滚。生产的回滚方式是：
--   ① 数据库只做向前兼容变更（加字段不删字段）→ 代码回滚即可，库不动
--   ② 真出事：用备份恢复（RTO ≤4h），不是执行本脚本
--
-- 本脚本的用途：开发机上迁移写错了、想重来一遍
-- 执行前置：已执行 mysqldump 备份，或确认库里没有要保留的数据
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 删除 V6 新增表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS ai_feedback;
DROP TABLE IF EXISTS house_embedding;

-- ---------------------------------------------------------------------
-- 2. 删除 V5 新增表与列
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS bill_item;

ALTER TABLE hrent_rent
    DROP INDEX uk_renting_period,
    DROP INDEX idx_tenant_status,
    DROP INDEX idx_status_due,
    DROP COLUMN period,
    DROP COLUMN amount_fen,
    DROP COLUMN paid_fen,
    DROP COLUMN bill_status,
    DROP COLUMN channel,
    DROP COLUMN voucher_pic,
    DROP COLUMN confirm_at,
    DROP COLUMN version;

-- ---------------------------------------------------------------------
-- 3. 删除 V4 新增表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS repair_log;
DROP TABLE IF EXISTS repair_image;
DROP TABLE IF EXISTS repair_order;

-- ---------------------------------------------------------------------
-- 4. 删除 V3 新增表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS rating_summary;
DROP TABLE IF EXISTS review_appeal;
DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS house_audit;

-- ---------------------------------------------------------------------
-- 5. 删除 V2 新增表与列
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS lease_log;
DROP TABLE IF EXISTS user_role;

ALTER TABLE hrent_house_renting
    DROP INDEX idx_tenant_status,
    DROP INDEX idx_house_status,
    DROP INDEX idx_actual_end,
    DROP COLUMN money_fen,
    DROP COLUMN start_date,
    DROP COLUMN end_date,
    DROP COLUMN status,
    DROP COLUMN actual_end_date,
    DROP COLUMN version;

ALTER TABLE hrent_apply_rent
    DROP COLUMN deposit_fen;

ALTER TABLE hrent_house_resource
    DROP INDEX idx_list,
    DROP INDEX idx_city_price,
    DROP COLUMN money_fen;

ALTER TABLE sys_user
    DROP COLUMN pwd_algo,
    DROP COLUMN real_name_status;

-- ---------------------------------------------------------------------
-- 6. 清 Flyway 历史（否则下次启动会认为已迁移过，跳过所有 V 脚本）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS flyway_schema_history;

SELECT '回滚完成：已恢复到 V1 基线状态' AS 结果;
