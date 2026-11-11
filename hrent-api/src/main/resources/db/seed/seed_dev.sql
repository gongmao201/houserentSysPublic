-- =====================================================================
-- 开发/演示种子数据（不是迁移脚本，不随 Flyway 自动执行）
-- 规模对齐 09 号文档"数字唯一来源表"：60 套房 / 41 条租约（含 1 条超窗负样本）/ 50 条评价 / 90 条账单
--
-- 执行：
--   mysql -uroot -p --default-character-set=utf8mb4 house_rent < seed_dev.sql
-- 重置：
--   mysql -uroot -p house_rent < reset_dev.sql
--
-- ID 约定（全部以 SEED 开头，便于一键清理）：
--   房东 SEEDL1..3   租客 SEEDT1..6   房源 SEEDH0001..0060
--   租约 SEEDG001..040   账单 SEEDR001-1..030-3   评价 SEEDVH/VL001..025
--   核验 SEEDA001..060   图片 SEEDP001..060
--
-- 注意：金额字段同时写 money(元) 与 money_fen(分)，保证老代码与新代码都能读
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. 幂等清理（只删 SEED 前缀，不碰真实数据）
-- ---------------------------------------------------------------------
DELETE FROM bill_item        WHERE rent_id   LIKE 'SEEDR%';
DELETE FROM hrent_rent       WHERE id        LIKE 'SEEDR%';
DELETE FROM review_appeal    WHERE review_id LIKE 'SEEDV%';
DELETE FROM review           WHERE id        LIKE 'SEEDV%';
DELETE FROM house_audit      WHERE id        LIKE 'SEEDA%';
DELETE FROM rating_summary   WHERE target_id LIKE 'SEEDH%' OR target_id LIKE 'SEEDL%';
DELETE FROM lease_log        WHERE lease_id  LIKE 'SEEDG%';
DELETE FROM hrent_house_renting WHERE id     LIKE 'SEEDG%';
DELETE FROM hrent_tag_and_house WHERE house_id LIKE 'SEEDH%';
DELETE FROM hrent_pic        WHERE id        LIKE 'SEEDP%';
DELETE FROM hrent_house_resource WHERE id    LIKE 'SEEDH%';
DELETE FROM user_role        WHERE user_id   LIKE 'SEED%';
DELETE FROM sys_user         WHERE id        LIKE 'SEED%';

-- ---------------------------------------------------------------------
-- 1. 用户：3 房东 + 6 租客（演示口令统一为 <DEMO_PASSWORD> = MD5）
-- ---------------------------------------------------------------------
INSERT INTO sys_user (id, role, role_str, name, login_name, password, pwd_algo, mobile, real_name_status, sex, create_by, create_time, del_flag)
VALUES
 ('SEEDL1', 1, '房东', '演示用户', 'seed_l1', '00000000000000000000000000000000', 'md5', '138****0000', 1, '0', '1', NOW(), 0),
 ('SEEDL2', 1, '房东', '演示用户', 'seed_l2', '00000000000000000000000000000000', 'md5', '138****0000', 1, '1', '1', NOW(), 0),
 ('SEEDL3', 1, '房东', '演示用户', 'seed_l3', '00000000000000000000000000000000', 'md5', '138****0000', 0, '0', '1', NOW(), 0),
 ('SEEDT1', 0, '租户', '演示用户', 'seed_t1', '00000000000000000000000000000000', 'md5', '138****0000', 1, '1', '1', NOW(), 0),
 ('SEEDT2', 0, '租户', '演示用户', 'seed_t2', '00000000000000000000000000000000', 'md5', '138****0000', 1, '0', '1', NOW(), 0),
 ('SEEDT3', 0, '租户', '演示用户', 'seed_t3', '00000000000000000000000000000000', 'md5', '138****0000', 0, '1', '1', NOW(), 0),
 ('SEEDT4', 0, '租户', '演示用户', 'seed_t4', '00000000000000000000000000000000', 'md5', '138****0000', 1, '0', '1', NOW(), 0),
 ('SEEDT5', 0, '租户', '演示用户', 'seed_t5', '00000000000000000000000000000000', 'md5', '138****0000', 0, '1', '1', NOW(), 0),
 ('SEEDT6', 0, '租户', '演示用户', 'seed_t6', '00000000000000000000000000000000', 'md5', '138****0000', 1, '0', '1', NOW(), 0);

INSERT INTO user_role (id, user_id, role, create_time)
SELECT CONCAT('UR', u.id), u.id,
       CASE u.role WHEN 0 THEN 'TENANT' WHEN 1 THEN 'LANDLORD' ELSE 'ADMIN' END, NOW()
FROM sys_user u WHERE u.id LIKE 'SEED%';

-- ---------------------------------------------------------------------
-- 2. 房源 60 套（3 个虚构小区 × 20 套）
-- ---------------------------------------------------------------------
INSERT INTO hrent_house_resource
 (id, province, city, county, address, money, money_fen, area, `describe`, house_type, floor, build_time,
  rent_type, people_num, latitude, longitude, state, create_by, create_time, update_by, update_time, del_flag)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 60)
SELECT CONCAT('SEEDH', LPAD(n, 4, '0')),
       '四川省', '成都市',
       ELT(1 + (n % 3), '武侯区', '高新区', '锦江区'),
       CONCAT(ELT(1 + (n % 3), '云栖花园', '锦官苑', '天府雅居'), ' ',
              1 + (n DIV 20), '栋', 100 + n, '室'),
       1200 + (n % 12) * 200,
       (1200 + (n % 12) * 200) * 100,
       40 + (n % 8) * 10,
       CONCAT('SEED 演示房源｜', ELT(1 + (n % 3), '云栖花园', '锦官苑', '天府雅居'),
              '｜采光好，近地铁，可养宠，拎包入住'),
       CAST(1 + (n % 6) AS CHAR),
       3 + (n % 25), '2015',
       n % 2, 1,
       ROUND(30.600000 + n * 0.001, 6),
       ROUND(104.000000 + n * 0.001, 6),
       1, ELT(1 + (n % 3), 'SEEDL1', 'SEEDL2', 'SEEDL3'), NOW(), '1', NOW(), 0
FROM seq;

-- 房源主图（复用仓库内置占位图，后续用 ImageGen 替换为真实风格图）
INSERT INTO hrent_pic (id, name, type, state, url, house_id, create_by, create_time, update_by, update_time, del_flag)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 60)
SELECT CONCAT('SEEDP', LPAD(n, 3, '0')), CONCAT('房源图-', n), 5, 1,
       CONCAT('/default/house/house', 1 + (n % 2), '.png'),
       CONCAT('SEEDH', LPAD(n, 4, '0')), '2', NOW(), '2', NOW(), 0
FROM s;

-- 每个房源挂 2 个标签（4 个标签轮换，保证推荐场景有区分度）
INSERT INTO hrent_tag_and_house (id, house_id, tag_id, create_by, create_time, update_by, update_time, del_flag)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 60)
SELECT CONCAT('STH', LPAD(n, 4, '0'), '-', k.k),
       CONCAT('SEEDH', LPAD(n, 4, '0')),
       ELT(1 + ((n + k.k) % 4), '9001', '9002', '9003', '9004'),
       '2', NOW(), '2', NOW(), 0
FROM s
CROSS JOIN (SELECT 1 AS k UNION ALL SELECT 2) k;

-- ---------------------------------------------------------------------
-- 3. 租约 41 条
--    1..25  已退租（status=2，实际退租日都在 30 天内 → 可评价）
--    26..35 生效中（status=0）
--    36..40 已到期未退租（status=1）
--    41     负样本：45 天前退租 → 超出评价窗口，用于测"第 31 天拒绝"
-- ---------------------------------------------------------------------
INSERT INTO hrent_house_renting
 (id, house_id, contract, tenant_id, money, money_fen, start_time, end_time,
  start_date, end_date, status, actual_end_date, version, create_by, create_time, update_by, update_time, del_flag)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 41)
SELECT CONCAT('SEEDG', LPAD(n, 3, '0')),
       CONCAT('SEEDH', LPAD(n, 4, '0')),
       NULL,
       ELT(1 + (n % 6), 'SEEDT1', 'SEEDT2', 'SEEDT3', 'SEEDT4', 'SEEDT5', 'SEEDT6'),
       1200 + (n % 12) * 200,
       (1200 + (n % 12) * 200) * 100,
       DATE_FORMAT(IF(n <= 25 OR n = 41, CURDATE() - INTERVAL 400 DAY,
                      IF(n <= 35, CURDATE() - INTERVAL 90 DAY, CURDATE() - INTERVAL 400 DAY)), '%Y-%m-%d 00:00:00'),
       DATE_FORMAT(IF(n <= 25 OR n = 41, CURDATE() - INTERVAL 60 DAY,
                      IF(n <= 35, CURDATE() + INTERVAL 275 DAY, CURDATE() - INTERVAL 10 DAY)), '%Y-%m-%d 23:59:59'),
       IF(n <= 25 OR n = 41, CURDATE() - INTERVAL 400 DAY, IF(n <= 35, CURDATE() - INTERVAL 90 DAY, CURDATE() - INTERVAL 400 DAY)),
       IF(n <= 25 OR n = 41, CURDATE() - INTERVAL 60 DAY,  IF(n <= 35, CURDATE() + INTERVAL 275 DAY, CURDATE() - INTERVAL 10 DAY)),
       IF(n <= 25 OR n = 41, 2, IF(n <= 35, 0, 1)),
       IF(n <= 25, CURDATE() - INTERVAL (n % 20) DAY, IF(n = 41, CURDATE() - INTERVAL 45 DAY, NULL)),
       0,
       ELT(1 + (n % 3), 'SEEDL1', 'SEEDL2', 'SEEDL3'), NOW(), '1', NOW(), 0
FROM seq;

-- 租约流转日志（已退租的补两条，时间线才有内容）
INSERT INTO lease_log (id, lease_id, from_status, to_status, operator_id, remark, create_time)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 25)
SELECT CONCAT('SLG', LPAD(n, 3, '0'), '-', k.k),
       CONCAT('SEEDG', LPAD(n, 3, '0')),
       ELT(k.k, 0, 0), ELT(k.k, 0, 2),
       ELT(k.k, 'SEEDL1', 'SEEDT1'),
       ELT(k.k, '租约生效', '双方确认退租'), NOW()
FROM s
CROSS JOIN (SELECT 1 AS k UNION ALL SELECT 2) k;

-- ---------------------------------------------------------------------
-- 4. 评价 50 条（25 条房源 + 25 条房东），只挂在已退租的租约上
-- ---------------------------------------------------------------------
INSERT INTO review (id, lease_id, reviewer_id, target_type, target_id, score, dimensions, content, images, status, create_time)
SELECT CONCAT('SEEDVH', LPAD(g.n, 3, '0')),
       CONCAT('SEEDG', LPAD(g.n, 3, '0')),
       g.tenant_id, 'HOUSE', g.house_id,
       3.0 + (g.n % 5) * 0.5,
       JSON_OBJECT('decoration', 3.0 + (g.n % 5) * 0.5,
                   'location',   3.5,
                   'costPerformance', 3.0 + ((g.n + 1) % 5) * 0.5,
                   'landlordCoop',    4.0,
                   'noise',      3.0 + ((g.n + 2) % 4) * 0.5),
       CONCAT('SEED 演示评价：位置方便，房东响应及时。编号 ', g.n),
       JSON_ARRAY(),
       'PUBLISHED',
       DATE_SUB(CURDATE(), INTERVAL (g.n % 20) DAY)
FROM (SELECT g.*, h.create_by AS landlord_id,
             CAST(SUBSTRING(g.id, 6) AS UNSIGNED) AS n
      FROM hrent_house_renting g JOIN hrent_house_resource h ON h.id = g.house_id
      WHERE g.id LIKE 'SEEDG%' AND g.status = 2
        AND CAST(SUBSTRING(g.id, 6) AS UNSIGNED) <= 25) g;

INSERT INTO review (id, lease_id, reviewer_id, target_type, target_id, score, dimensions, content, images, status, create_time)
SELECT CONCAT('SEEDVL', LPAD(g.n, 3, '0')),
       CONCAT('SEEDG', LPAD(g.n, 3, '0')),
       g.tenant_id, 'LANDLORD', g.landlord_id,
       3.0 + ((g.n + 2) % 5) * 0.5,
       JSON_OBJECT('responseSpeed', 4.0,
                   'repairSpeed',   3.5,
                   'depositRefund', 4.0,
                   'communication', 4.5),
       CONCAT('SEED 演示评价：退押金干脆，沟通顺畅。编号 ', g.n),
       JSON_ARRAY(),
       'PUBLISHED',
       DATE_SUB(CURDATE(), INTERVAL (g.n % 20) DAY)
FROM (SELECT g.*, h.create_by AS landlord_id,
             CAST(SUBSTRING(g.id, 6) AS UNSIGNED) AS n
      FROM hrent_house_renting g JOIN hrent_house_resource h ON h.id = g.house_id
      WHERE g.id LIKE 'SEEDG%' AND g.status = 2
        AND CAST(SUBSTRING(g.id, 6) AS UNSIGNED) <= 25) g;

-- ---------------------------------------------------------------------
-- 5. 房源核验单 60 条（8 项清单，分数按权重算）
-- ---------------------------------------------------------------------
INSERT INTO house_audit
 (id, house_id, auditor_id, photo_real, address_verified, facility_match, price_reasonable,
  contract_uploaded, safety_checked, deposit_rule_clear, no_complaint, score, conclusion, create_time)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 60)
SELECT CONCAT('SEEDA', LPAD(n, 3, '0')),
       CONCAT('SEEDH', LPAD(n, 4, '0')), '1',
       1, 1, IF(n % 5 = 0, 0, 1), 1, 1, IF(n % 7 = 0, 0, 1), 1, IF(n % 11 = 0, 0, 1),
       ROUND((0.20 * 1 + 0.20 * 1 + 0.15 * IF(n % 5 = 0, 0, 1) + 0.15 * 1
            + 0.10 * 1 + 0.10 * IF(n % 7 = 0, 0, 1) + 0.05 * 1 + 0.05 * IF(n % 11 = 0, 0, 1)) * 5, 2),
       'SEED 核验通过', NOW()
FROM seq;

-- ---------------------------------------------------------------------
-- 6. 账单 90 条（租约 1..30 × 最近 3 个月）+ 明细 360 条
--    前 10 个租约已缴、11~20 已标记待确认、21~30 未缴（部分逾期）
-- ---------------------------------------------------------------------
INSERT INTO hrent_rent
 (id, renting_id, period, amount_fen, paid_fen, payable_time, pay_time, tenant_id, state, bill_status,
  channel, voucher_pic, confirm_at, version, create_by, create_time, update_by, update_time, del_flag)
SELECT CONCAT('SEEDR', LPAD(g.n, 3, '0'), '-', m.m),
       CONCAT('SEEDG', LPAD(g.n, 3, '0')),
       DATE_FORMAT(CURDATE() - INTERVAL (m.m - 1) MONTH, '%Y-%m'),
       g.money_fen,
       IF(g.n <= 10, g.money_fen, 0),
       DATE_ADD(DATE_FORMAT(CURDATE() - INTERVAL (m.m - 1) MONTH, '%Y-%m-01'), INTERVAL 4 DAY),
       IF(g.n <= 10, DATE_ADD(DATE_FORMAT(CURDATE() - INTERVAL (m.m - 1) MONTH, '%Y-%m-01'), INTERVAL 6 DAY), NULL),
       g.tenant_id,
       IF(g.n <= 10, 2, IF(DATE_ADD(DATE_FORMAT(CURDATE() - INTERVAL (m.m - 1) MONTH, '%Y-%m-01'), INTERVAL 4 DAY) < NOW(), 1, 0)),
       CASE WHEN g.n <= 10 THEN 'PAID'
            WHEN g.n <= 20 THEN 'MARKED'
            WHEN DATE_ADD(DATE_FORMAT(CURDATE() - INTERVAL (m.m - 1) MONTH, '%Y-%m-01'), INTERVAL 4 DAY) < NOW() THEN 'OVERDUE'
            ELSE 'UNPAID' END,
       IF(g.n <= 20, 'offline_wechat', NULL),
       IF(g.n <= 10, '/default/house/house1.png', NULL),
       IF(g.n <= 10, DATE_ADD(DATE_FORMAT(CURDATE() - INTERVAL (m.m - 1) MONTH, '%Y-%m-01'), INTERVAL 7 DAY), NULL),
       0, '1', NOW(), '1', NOW(), 0
FROM (SELECT g.*, CAST(SUBSTRING(g.id, 6) AS UNSIGNED) AS n
      FROM hrent_house_renting g
      WHERE g.id LIKE 'SEEDG%' AND CAST(SUBSTRING(g.id, 6) AS UNSIGNED) <= 30) g
CROSS JOIN (SELECT 1 AS m UNION ALL SELECT 2 UNION ALL SELECT 3) m;

INSERT INTO bill_item (id, rent_id, item_type, amount_fen, remark, sort)
SELECT CONCAT(r.id, '-', k.k), r.id, ELT(k.k, 'RENT', 'WATER', 'ELECTRIC', 'PROPERTY'),
       CASE k.k
           WHEN 1 THEN FLOOR(r.amount_fen * 88 / 100)
           WHEN 2 THEN FLOOR(r.amount_fen * 4 / 100)
           WHEN 3 THEN FLOOR(r.amount_fen * 6 / 100)
           ELSE r.amount_fen - FLOOR(r.amount_fen * 88 / 100) - FLOOR(r.amount_fen * 4 / 100) - FLOOR(r.amount_fen * 6 / 100)
       END,
       ELT(k.k, '月租', '水费', '电费', '物业费'), k.k
FROM hrent_rent r
CROSS JOIN (SELECT 1 AS k UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) k
WHERE r.id LIKE 'SEEDR%';

-- ---------------------------------------------------------------------
-- 7. 刷新评分汇总（与 V3 同一套公式，保证种子数据也能被列表排序用到）
-- ---------------------------------------------------------------------
INSERT INTO rating_summary (target_type, target_id, official_score, user_score, display_score, review_count)
SELECT t.target_type, t.target_id, NULL,
       ROUND(AVG(t.score), 2),
       ROUND((5 * 4.0 + SUM(t.score)) / (5 + COUNT(*)), 2),
       COUNT(*)
FROM review t
WHERE t.del_flag = 0 AND t.status = 'PUBLISHED' AND t.id LIKE 'SEEDV%'
GROUP BY t.target_type, t.target_id
ON DUPLICATE KEY UPDATE
    user_score = VALUES(user_score), display_score = VALUES(display_score), review_count = VALUES(review_count);

INSERT INTO rating_summary (target_type, target_id, official_score, display_score, review_count)
SELECT 'HOUSE', a.house_id, a.score, 4.00, 0
FROM house_audit a
WHERE a.id LIKE 'SEEDA%'
ON DUPLICATE KEY UPDATE official_score = VALUES(official_score);

-- ---------------------------------------------------------------------
-- 8. 执行结果自检（应输出 60 / 41 / 50 / 90 / 360）
-- ---------------------------------------------------------------------
SELECT '房源' AS 对象, COUNT(*) AS 条数 FROM hrent_house_resource WHERE id LIKE 'SEEDH%'
UNION ALL SELECT '租约', COUNT(*) FROM hrent_house_renting WHERE id LIKE 'SEEDG%'
UNION ALL SELECT '评价', COUNT(*) FROM review WHERE id LIKE 'SEEDV%'
UNION ALL SELECT '账单', COUNT(*) FROM hrent_rent WHERE id LIKE 'SEEDR%'
UNION ALL SELECT '账单明细', COUNT(*) FROM bill_item WHERE id LIKE 'SEEDR%';
