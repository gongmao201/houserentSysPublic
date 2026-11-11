-- =====================================================================
-- 数据对账脚本（QA 与 BE 共用，可随时重跑）
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 house_rent < verify.sql
-- 判定：每一项都必须落在"期望"列；任何一项不符，不许进联调
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 迁移完整性：该有的表和字段在不在
-- ---------------------------------------------------------------------
SELECT '1.1 表数量（期望 25，不含 flyway_schema_history）' AS 检查项, COUNT(*) AS 实际, 25 AS 期望
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
  AND table_name <> 'flyway_schema_history';

SELECT '1.2 缺失的迁移字段（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM (
    SELECT 'hrent_house_resource.money_fen' AS col FROM DUAL
    UNION ALL SELECT 'hrent_house_renting.status' FROM DUAL
    UNION ALL SELECT 'hrent_house_renting.actual_end_date' FROM DUAL
    UNION ALL SELECT 'hrent_rent.bill_status' FROM DUAL
    UNION ALL SELECT 'hrent_rent.amount_fen' FROM DUAL
    UNION ALL SELECT 'sys_user.pwd_algo' FROM DUAL
) req
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND CONCAT(c.table_name, '.', c.column_name) = req.col
);

-- ---------------------------------------------------------------------
-- 2. 金额对账：元字段 × 100 必须等于分字段（误差 0）
-- ---------------------------------------------------------------------
SELECT '2.1 房源金额不一致行数（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM hrent_house_resource
WHERE money IS NOT NULL AND ABS(ROUND(money * 100) - money_fen) > 0;

SELECT '2.2 房源金额汇总差异（期望 0.00）' AS 检查项,
       ROUND(SUM(money) - SUM(money_fen) / 100, 2) AS 实际, 0.00 AS 期望
FROM hrent_house_resource WHERE money IS NOT NULL;

SELECT '2.3 租约金额不一致行数（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM hrent_house_renting
WHERE money IS NOT NULL AND ABS(ROUND(money * 100) - money_fen) > 0;

-- ---------------------------------------------------------------------
-- 3. 账单：明细之和必须等于应收（欠 1 分也是错账）
-- ---------------------------------------------------------------------
SELECT '3.1 明细与应收不符的账单数（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM (
    SELECT r.id, r.amount_fen, COALESCE(SUM(i.amount_fen), 0) AS item_sum
    FROM hrent_rent r LEFT JOIN bill_item i ON i.rent_id = r.id
    WHERE r.amount_fen IS NOT NULL
    GROUP BY r.id, r.amount_fen
) t
WHERE t.item_sum <> t.amount_fen;

SELECT '3.2 无明细的账单数（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM hrent_rent r
WHERE r.amount_fen IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM bill_item i WHERE i.rent_id = r.id);

SELECT '3.3 同租约同账期重复账单（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM (
    SELECT renting_id, period FROM hrent_rent
    WHERE period IS NOT NULL GROUP BY renting_id, period HAVING COUNT(*) > 1
) d;

-- ---------------------------------------------------------------------
-- 4. 评分：汇总必须与明细一致
-- ---------------------------------------------------------------------
SELECT '4.1 汇总评价数与明细不符（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM rating_summary s
JOIN (
    SELECT target_type, target_id, COUNT(*) AS cnt
    FROM review WHERE del_flag = 0 AND status = 'PUBLISHED'
    GROUP BY target_type, target_id
) r ON r.target_type = s.target_type AND r.target_id = s.target_id
WHERE s.review_count <> r.cnt;

SELECT '4.2 展示分越界（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM rating_summary WHERE display_score < 1.0 OR display_score > 5.0;

SELECT '4.3 评分越界（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM review WHERE score < 1.0 OR score > 5.0;

-- ---------------------------------------------------------------------
-- 5. 评价窗口：只有"已退租 + 30 天内"的租约才允许评价
-- ---------------------------------------------------------------------
SELECT '5.1 挂在非退租租约上的评价（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM review v JOIN hrent_house_renting g ON g.id = v.lease_id
WHERE g.status <> 2;

SELECT '5.2 超出 30 天窗口的评价（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM review v JOIN hrent_house_renting g ON g.id = v.lease_id
WHERE g.actual_end_date IS NOT NULL
  AND v.create_time > DATE_ADD(g.actual_end_date, INTERVAL 30 DAY);

SELECT '5.3 当前可评价的租约数（信息项）' AS 检查项, COUNT(*) AS 实际, NULL AS 期望
FROM hrent_house_renting
WHERE status = 2 AND actual_end_date IS NOT NULL
  AND CURDATE() <= DATE_ADD(actual_end_date, INTERVAL 30 DAY);

-- ---------------------------------------------------------------------
-- 6. 租约状态分布（种子数据期望：生效 10 / 到期 5 / 退租 26，其中 1 条为超窗负样本）
-- ---------------------------------------------------------------------
SELECT CASE status WHEN 0 THEN '生效中' WHEN 1 THEN '已到期'
                    WHEN 2 THEN '已退租' ELSE '已解约' END AS 状态,
       COUNT(*) AS 条数
FROM hrent_house_renting GROUP BY status ORDER BY status;

-- ---------------------------------------------------------------------
-- 7. 字符集与排序规则（必须全库一致，否则 JOIN 会报 Illegal mix of collations）
-- ---------------------------------------------------------------------
SELECT '7.1 非 utf8mb4 的表（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_collation NOT LIKE 'utf8mb4%';

SELECT '7.2 排序规则不一致的表（期望 1 种）' AS 检查项,
       COUNT(DISTINCT table_collation) AS 实际, 1 AS 期望
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';

-- ---------------------------------------------------------------------
-- 8. 索引：高频查询必须走索引（缺失即性能债）
-- ---------------------------------------------------------------------
SELECT '8.1 关键索引缺失数（期望 0）' AS 检查项, COUNT(*) AS 实际, 0 AS 期望
FROM (
    SELECT 'hrent_house_resource.idx_list' AS idx FROM DUAL
    UNION ALL SELECT 'hrent_rent.uk_renting_period' FROM DUAL
    UNION ALL SELECT 'review.uk_reviewer_target' FROM DUAL
    UNION ALL SELECT 'repair_order.uk_idem' FROM DUAL
    UNION ALL SELECT 'hrent_house_renting.idx_tenant_status' FROM DUAL
) req
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE() AND CONCAT(s.table_name, '.', s.index_name) = req.idx
);

-- ---------------------------------------------------------------------
-- 9. 时区（写入与读取必须同为 +08:00）
-- ---------------------------------------------------------------------
SELECT '9.1 会话时区' AS 检查项, @@session.time_zone AS 值, 'SYSTEM 或 +08:00（JDBC 必须显式传 serverTimezone=Asia/Shanghai）' AS 期望;
SELECT '9.2 当前时间' AS 检查项, NOW() AS 值, '应与北京时间一致' AS 期望;
