-- =====================================================================
-- preflight.sql · Flyway 迁移前只读预检
-- =====================================================================
-- 用法：mysql -uroot -p house_rent < db/preflight.sql
-- 性质：只读，不修改任何数据；所有结果人工看一眼再决定 baseline。
-- 判定表见 docs/15 §7、docs/17 §5。
-- =====================================================================

-- ---------------------------------------------------------------------
-- P1 当前库与表数：0=空库，13=干净老库，25=已手动跑过 V1–V7（或已由 Flyway 执行）
-- ---------------------------------------------------------------------
SELECT DATABASE() AS db_name,
       @@version   AS mysql_version,
       (SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = DATABASE()) AS table_count;

-- ---------------------------------------------------------------------
-- P2 Flyway 历史表：存在说明接接入过；success=0 说明上次失败
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS flyway_history_exists
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history';

-- 仅当 P2=1 时执行下面这条：
-- SELECT installed_rank, version, description, type, success, installed_on
-- FROM flyway_schema_history ORDER BY installed_rank;

-- ---------------------------------------------------------------------
-- P3 V2/V5 新增列是否存在（18 列）
-- ---------------------------------------------------------------------
SELECT table_name, column_name, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'sys_user'             AND column_name IN ('pwd_algo','real_name_status')) OR
    (table_name = 'hrent_house_resource' AND column_name IN ('money_fen')) OR
    (table_name = 'hrent_apply_rent'     AND column_name IN ('deposit_fen')) OR
    (table_name = 'hrent_house_renting'  AND column_name IN ('money_fen','start_date','end_date','status','actual_end_date','version')) OR
    (table_name = 'hrent_rent'           AND column_name IN ('period','amount_fen','paid_fen','bill_status','channel','voucher_pic','confirm_at','version'))
  )
ORDER BY table_name, column_name;

-- ---------------------------------------------------------------------
-- P4 12 张新表是否存在
-- ---------------------------------------------------------------------
SELECT 'user_role' AS expected_table,
       IF(COUNT(*) > 0, 'OK', 'MISSING') AS status
FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'user_role'
UNION ALL SELECT 'lease_log',       IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'lease_log'
UNION ALL SELECT 'house_audit',     IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'house_audit'
UNION ALL SELECT 'review',          IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'review'
UNION ALL SELECT 'review_appeal',   IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'review_appeal'
UNION ALL SELECT 'rating_summary',  IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'rating_summary'
UNION ALL SELECT 'repair_order',    IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'repair_order'
UNION ALL SELECT 'repair_image',    IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'repair_image'
UNION ALL SELECT 'repair_log',      IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'repair_log'
UNION ALL SELECT 'bill_item',       IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'bill_item'
UNION ALL SELECT 'house_embedding', IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'house_embedding'
UNION ALL SELECT 'ai_feedback',     IF(COUNT(*) > 0, 'OK', 'MISSING') FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'ai_feedback';

-- ---------------------------------------------------------------------
-- P5 V2 断言预演：空串日期会让 STR_TO_DATE 返回 NULL，导致 V2 中断
--    期望：empty_start = 0 且 empty_end = 0；>0 先清洗再迁移
-- ---------------------------------------------------------------------
SELECT SUM(start_time IS NOT NULL AND TRIM(start_time) = '') AS empty_start,
       SUM(end_time   IS NOT NULL AND TRIM(end_time)   = '') AS empty_end,
       COUNT(*) AS total_rows
FROM hrent_house_renting;

-- 清洗（需人工确认后执行，不在本脚本里跑）：
-- UPDATE hrent_house_renting SET start_time = NULL WHERE TRIM(start_time) = '';
-- UPDATE hrent_house_renting SET end_time   = NULL WHERE TRIM(end_time)   = '';

-- ---------------------------------------------------------------------
-- P6 断言预演：金额/角色/租约状态
--    迁移前这些列不存在，这里只统计"源数据有多少行需要回填"
-- ---------------------------------------------------------------------
SELECT '房源租金待回填' AS rule,
       COUNT(*) AS rows_need_backfill
FROM hrent_house_resource
WHERE money IS NOT NULL
UNION ALL
SELECT '租房押金待回填', COUNT(*)
FROM hrent_apply_rent
WHERE deposit IS NOT NULL
UNION ALL
SELECT '租约租金待回填', COUNT(*)
FROM hrent_house_renting
WHERE money IS NOT NULL
UNION ALL
SELECT '租约日期待回填', COUNT(*)
FROM hrent_house_renting
WHERE end_time IS NOT NULL
UNION ALL
SELECT '用户角色待回填', COUNT(*)
FROM sys_user
WHERE del_flag = 0;

-- ---------------------------------------------------------------------
-- P7 字符集/排序规则/时区
--    期望：全部 utf8mb4_general_ci；时区 +08:00 或 UTC（看部署）
-- ---------------------------------------------------------------------
SELECT @@character_set_database AS db_charset,
       @@collation_database       AS db_collation,
       @@session.time_zone        AS session_tz,
       NOW()                      AS now_time;

SELECT table_name, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_collation <> 'utf8mb4_general_ci';

-- ---------------------------------------------------------------------
-- P8 老库 admin 检查：V1 的 INSERT IGNORE 可能多插一个 admin
-- ---------------------------------------------------------------------
SELECT id, login_name, role, name
FROM sys_user
WHERE login_name = 'admin';

-- =====================================================================
-- 判定速查
--   P1=0                -> 空库，baseline 0，跑 V1–V7
--   P1=13 且 P3/P4 全空 -> 干净老库，baseline 0
--   P1=25 且 P3/P4 全有 -> 已手动跑过 V6/V7，baseline 6
--   13<P1<25 或 P3 部分  -> 半迁移，禁止自动，先备份恢复/回滚
--   P2=1 且 success=0    -> 修数据后 flyway repair，不要直接重跑
-- =====================================================================
