-- =====================================================================
-- W9 演示/联调种子数据（不是 Flyway 迁移，不自动执行）
-- 规模：200 套房 / 53 条租约 / 50 条评价 / 90 条账单（30 租约 × 3 个月）
-- 账号：3 房东 W9L1..W9L3，6 租客 W9T1..W9T6，口令统一 123456
--
-- 执行：
--   mysql -uroot -proot --default-character-set=utf8mb4 house_rent_w5 \
--     -e "source hrent-api/src/main/resources/db/seed/seed_w9_200.sql"
-- 或：
--   powershell -File scripts/seed_w9.ps1
--
-- 幂等：只删除 W9 前缀的种子行，再重建；不会碰 admin 与真实房源。
-- 关键样本（越权用例直接引用）：
--   房源 W9H0001（房东 W9L2） / W9H0002（房东 W9L3）
--   租约 W9G030（房东 W9L1 / 租客 W9T1，ACTIVE）
--   租约 W9G041（房东 W9L1 / 租客 W9L1，多角色账号自评负样本，已退租超窗）
--   租约 W9G042（房东 W9L1 / 租客 W9T1，TERMINATED，可提交评价）
--   账单 W9R030-1（租客 W9T1，房东 W9L1）
--   评价 W9VH006（房源 W9H0006，房东 W9L1，租客 W9T1）
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. 幂等清理（只删 W9 前缀）
-- ---------------------------------------------------------------------
DELETE FROM bill_item          WHERE rent_id   LIKE 'W9R%';
DELETE FROM hrent_rent         WHERE id        LIKE 'W9R%';
DELETE FROM repair_image       WHERE repair_id IN (SELECT id FROM repair_order WHERE tenant_id LIKE 'W9%' OR house_id LIKE 'W9H%');
DELETE FROM repair_log         WHERE repair_id IN (SELECT id FROM repair_order WHERE tenant_id LIKE 'W9%' OR house_id LIKE 'W9H%');
DELETE FROM repair_order       WHERE tenant_id LIKE 'W9%' OR house_id LIKE 'W9H%';
DELETE FROM review_appeal      WHERE review_id LIKE 'W9V%' OR applicant_id LIKE 'W9%';
DELETE FROM review             WHERE id        LIKE 'W9V%' OR lease_id LIKE 'W9G%' OR reviewer_id LIKE 'W9%';
DELETE FROM house_audit        WHERE id        LIKE 'W9A%';
DELETE FROM rating_summary     WHERE target_id LIKE 'W9H%' OR target_id LIKE 'W9L%';
DELETE FROM lease_log          WHERE lease_id  LIKE 'W9G%';
DELETE FROM hrent_house_renting WHERE id       LIKE 'W9G%';
DELETE FROM hrent_tag_and_house WHERE house_id LIKE 'W9H%';
DELETE FROM hrent_pic          WHERE id        LIKE 'W9P%';
DELETE FROM hrent_house_resource WHERE id      LIKE 'W9H%';
DELETE FROM user_role          WHERE user_id   LIKE 'W9%';
DELETE FROM sys_user           WHERE id        LIKE 'W9%';

-- ---------------------------------------------------------------------
-- 1. 用户：3 房东 + 6 租客（MD5('123456')，与 admin 同口令）
-- ---------------------------------------------------------------------
INSERT INTO sys_user (id, role, role_str, name, login_name, password, pwd_algo, mobile, real_name_status, sex, create_by, create_time, del_flag)
VALUES
 ('W9L1', 1, '房东', '演示房东一', 'seed_l1', 'e10adc3949ba59abbe56e057f20f883e', 'md5', '13800000001', 1, '0', '1', NOW(), 0),
 ('W9L2', 1, '房东', '演示房东二', 'seed_l2', 'e10adc3949ba59abbe56e057f20f883e', 'md5', '13800000002', 1, '1', '1', NOW(), 0),
 ('W9L3', 1, '房东', '演示房东三', 'seed_l3', 'e10adc3949ba59abbe56e057f20f883e', 'md5', '13800000003', 0, '0', '1', NOW(), 0),
 ('W9T1', 0, '租户', '演示租客一', 'seed_t1', 'e10adc3949ba59abbe56e057f20f883e', 'md5', '13900000001', 1, '1', '1', NOW(), 0),
 ('W9T2', 0, '租户', '演示租客二', 'seed_t2', 'e10adc3949ba59abbe56e057f20f883e', 'md5', '13900000002', 1, '0', '1', NOW(), 0),
 ('W9T3', 0, '租户', '演示租客三', 'seed_t3', 'e10adc3949ba59abbe56e057f20f883e', 'md5', '13900000003', 0, '1', '1', NOW(), 0),
 ('W9T4', 0, '租户', '演示租客四', 'seed_t4', 'e10adc3949ba59abbe56e057f20f883e', 'md5', '13900000004', 1, '0', '1', NOW(), 0),
 ('W9T5', 0, '租户', '演示租客五', 'seed_t5', 'e10adc3949ba59abbe56e057f20f883e', 'md5', '13900000005', 0, '1', '1', NOW(), 0),
 ('W9T6', 0, '租户', '演示租客六', 'seed_t6', 'e10adc3949ba59abbe56e057f20f883e', 'md5', '13900000006', 1, '0', '1', NOW(), 0);

INSERT INTO user_role (id, user_id, role, create_time)
SELECT CONCAT('UR', u.id), u.id,
       CASE u.role WHEN 0 THEN 'TENANT' WHEN 1 THEN 'LANDLORD' ELSE 'ADMIN' END, NOW()
FROM sys_user u WHERE u.id LIKE 'W9%';

-- ---------------------------------------------------------------------
-- 2. 标签 4 个（推荐接口按 hrent_tag.name 匹配）
-- ---------------------------------------------------------------------
INSERT INTO hrent_tag (id, name, rgb, state, create_by, create_time, update_by, update_time, del_flag)
VALUES
 ('W9TAG1', '养宠', '#67C23A', '1', '1', NOW(), '1', NOW(), 0),
 ('W9TAG2', '近地铁', '#409EFF', '1', '1', NOW(), '1', NOW(), 0),
 ('W9TAG3', '有电梯', '#E6A23C', '1', '1', NOW(), '1', NOW(), 0),
 ('W9TAG4', '采光好', '#F56C6C', '1', '1', NOW(), '1', NOW(), 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), state = '1', del_flag = 0, update_time = NOW();

-- ---------------------------------------------------------------------
-- 3. 房源 200 套（3 个小区/区县轮换，价格 1200–3100 元）
-- ---------------------------------------------------------------------
INSERT INTO hrent_house_resource
 (id, province, city, county, address, money, money_fen, area, `describe`, house_type, floor, build_time,
  rent_type, people_num, latitude, longitude, state, create_by, create_time, update_by, update_time, del_flag)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 200)
SELECT CONCAT('W9H', LPAD(n, 4, '0')),
       '广东省', '广州市',
       ELT(1 + (n % 3), '天河区', '海珠区', '番禺区'),
       CONCAT(ELT(1 + (n % 3), '天河云栖花园', '海珠锦官苑', '番禺天府雅居'), ' ',
              1 + (n DIV 20), '栋', 100 + n, '室'),
       1200 + (n % 20) * 100,
       (1200 + (n % 20) * 100) * 100,
       40 + (n % 8) * 10,
       CONCAT('W9 演示房源｜', ELT(1 + (n % 4), '可养宠，', '近地铁，', '有电梯，', '采光好，'),
              '拎包入住，家电齐全'),
       CONCAT(1 + ((n DIV 2) % 3), '室1厅'),
       3 + (n % 25), '2015',
       n % 2, 1,
       ROUND(23.100000 + n * 0.001, 6),
       ROUND(113.300000 + n * 0.001, 6),
       1, IF(n IN (41, 53), 'W9L1', ELT(1 + (n % 3), 'W9L1', 'W9L2', 'W9L3')), NOW(), '1', NOW(), 0
FROM seq;

-- 每套 1 张主图（复用仓库内置占位图，后续可替换 ImageGen 图）
INSERT INTO hrent_pic (id, name, type, state, url, house_id, create_by, create_time, update_by, update_time, del_flag)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 200)
SELECT CONCAT('W9P', LPAD(n, 4, '0')), CONCAT('房源图-', n), 5, 1,
       CONCAT('/default/house/house', 1 + (n % 2), '.png'),
       CONCAT('W9H', LPAD(n, 4, '0')), '2', NOW(), '2', NOW(), 0
FROM s;

-- 每套 2 个标签（4 个标签轮换，保证推荐筛选有区分度）
INSERT INTO hrent_tag_and_house (id, house_id, tag_id, create_by, create_time, update_by, update_time, del_flag)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 200)
SELECT CONCAT('W9TH', LPAD(n, 4, '0'), '-', k.k),
       CONCAT('W9H', LPAD(n, 4, '0')),
       CONCAT('W9TAG', 1 + ((n + k.k) % 4)),
       '2', NOW(), '2', NOW(), 0
FROM s
CROSS JOIN (SELECT 1 AS k UNION ALL SELECT 2) k;

-- ---------------------------------------------------------------------
-- 4. 租约 53 条：
--    1..25   已退租（30 天内，可评价，带 50 条评价）
--    26..35  生效中（账单 1..30 × 3 个月）
--    36..40  已到期未退租
--    41      超窗负样本（45 天前退租；租客与房东同为 W9L1）
--    42      已退租、未评价（/rating/submit 正向样本）
--    43..52  生效中（租约状态机/取消联调用）
--    53      生效中，报修专用（房东 W9L1 / 租客 W9T1，不被其他用例改动）
-- ---------------------------------------------------------------------
INSERT INTO hrent_house_renting
 (id, house_id, contract, tenant_id, money, money_fen, start_time, end_time,
  start_date, end_date, status, actual_end_date, version, create_by, create_time, update_by, update_time, del_flag)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 53)
SELECT CONCAT('W9G', LPAD(n, 3, '0')),
       CONCAT('W9H', LPAD(n, 4, '0')),
       NULL,
       IF(n = 41, 'W9L1', IF(n = 53, 'W9T1', CONCAT('W9T', 1 + (n % 6)))),
       1200 + (n % 20) * 100,
       (1200 + (n % 20) * 100) * 100,
       DATE_FORMAT(IF(n <= 25 OR n IN (41, 42), CURDATE() - INTERVAL 400 DAY,
                     IF(n <= 35, CURDATE() - INTERVAL 90 DAY,
                        IF(n <= 40, CURDATE() - INTERVAL 400 DAY, CURDATE() - INTERVAL 60 DAY))),
                   '%Y-%m-%d 00:00:00'),
       DATE_FORMAT(IF(n <= 25 OR n IN (41, 42), CURDATE() - INTERVAL 60 DAY,
                     IF(n <= 35, CURDATE() + INTERVAL 275 DAY,
                        IF(n <= 40, CURDATE() - INTERVAL 10 DAY, CURDATE() + INTERVAL 305 DAY))),
                   '%Y-%m-%d 23:59:59'),
       IF(n <= 25 OR n IN (41, 42), CURDATE() - INTERVAL 400 DAY,
          IF(n <= 35, CURDATE() - INTERVAL 90 DAY,
             IF(n <= 40, CURDATE() - INTERVAL 400 DAY, CURDATE() - INTERVAL 60 DAY))),
       IF(n <= 25 OR n IN (41, 42), CURDATE() - INTERVAL 60 DAY,
          IF(n <= 35, CURDATE() + INTERVAL 275 DAY,
             IF(n <= 40, CURDATE() - INTERVAL 10 DAY, CURDATE() + INTERVAL 305 DAY))),
       IF(n <= 25, 2, IF(n IN (41, 42), 2, IF(n <= 35, 0, IF(n <= 40, 1, 0)))), 
       IF(n <= 25, CURDATE() - INTERVAL (n % 20) DAY,
          IF(n = 41, CURDATE() - INTERVAL 45 DAY,
             IF(n = 42, CURDATE() - INTERVAL 3 DAY, NULL))),
       0,
       CONCAT('W9L', 1 + (n % 3)), NOW(), '1', NOW(), 0
FROM seq;

-- 租约日志：已退租的补生效 + 退租两条
INSERT INTO lease_log (id, lease_id, from_status, to_status, operator_id, remark, create_time)
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 25)
SELECT CONCAT('W9LG', LPAD(n, 3, '0'), '-', k.k),
       CONCAT('W9G', LPAD(n, 3, '0')),
       ELT(k.k, 0, 0), ELT(k.k, 0, 2),
       ELT(k.k, CONCAT('W9L', 1 + (n % 3)), CONCAT('W9T', 1 + (n % 6))),
       ELT(k.k, '租约生效', '双方确认退租'), NOW()
FROM s
CROSS JOIN (SELECT 1 AS k UNION ALL SELECT 2) k;

-- ---------------------------------------------------------------------
-- 5. 评价 50 条：25 条房源 + 25 条房东，只挂已退租且窗口内的 1..25 号租约
-- ---------------------------------------------------------------------
INSERT INTO review (id, lease_id, reviewer_id, target_type, target_id, score, dimensions, content, images, status, create_time)
SELECT CONCAT('W9VH', LPAD(g.n, 3, '0')),
       CONCAT('W9G', LPAD(g.n, 3, '0')),
       g.tenant_id, 'HOUSE', g.house_id,
       3.0 + (g.n % 5) * 0.5,
       JSON_OBJECT('decoration', 3.0 + (g.n % 5) * 0.5,
                   'location', 3.5,
                   'costPerformance', 3.0 + ((g.n + 1) % 5) * 0.5,
                   'landlordCoop', 4.0,
                   'noise', 3.0 + ((g.n + 2) % 4) * 0.5),
       CONCAT('W9 演示评价：位置方便，房东响应及时。编号 ', g.n),
       JSON_ARRAY(), 'PUBLISHED',
       DATE_SUB(CURDATE(), INTERVAL (g.n % 20) DAY)
FROM (SELECT g.*, h.create_by AS landlord_id,
             CAST(SUBSTRING(g.id, 4) AS UNSIGNED) AS n
      FROM hrent_house_renting g JOIN hrent_house_resource h ON h.id = g.house_id
      WHERE g.id LIKE 'W9G%' AND g.status = 2
        AND CAST(SUBSTRING(g.id, 4) AS UNSIGNED) <= 25) g;

INSERT INTO review (id, lease_id, reviewer_id, target_type, target_id, score, dimensions, content, images, status, create_time)
SELECT CONCAT('W9VL', LPAD(g.n, 3, '0')),
       CONCAT('W9G', LPAD(g.n, 3, '0')),
       g.tenant_id, 'LANDLORD', g.landlord_id,
       3.0 + ((g.n + 2) % 5) * 0.5,
       JSON_OBJECT('responseSpeed', 4.0, 'repairSpeed', 3.5,
                   'depositRefund', 4.0, 'communication', 4.5),
       CONCAT('W9 演示评价：退押金干脆，沟通顺畅。编号 ', g.n),
       JSON_ARRAY(), 'PUBLISHED',
       DATE_SUB(CURDATE(), INTERVAL (g.n % 20) DAY)
FROM (SELECT g.*, h.create_by AS landlord_id,
             CAST(SUBSTRING(g.id, 4) AS UNSIGNED) AS n
      FROM hrent_house_renting g JOIN hrent_house_resource h ON h.id = g.house_id
      WHERE g.id LIKE 'W9G%' AND g.status = 2
        AND CAST(SUBSTRING(g.id, 4) AS UNSIGNED) <= 25) g;

-- ---------------------------------------------------------------------
-- 6. 房源核验 200 条（8 项清单；分数按 docs 权重）
-- ---------------------------------------------------------------------
INSERT INTO house_audit
 (id, house_id, auditor_id, photo_real, address_verified, facility_match, price_reasonable,
  contract_uploaded, safety_checked, deposit_rule_clear, no_complaint, score, conclusion, create_time)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 200)
SELECT CONCAT('W9A', LPAD(n, 4, '0')),
       CONCAT('W9H', LPAD(n, 4, '0')), '1',
       1, 1, IF(n % 5 = 0, 0, 1), 1, 1, IF(n % 7 = 0, 0, 1), 1, IF(n % 11 = 0, 0, 1),
       ROUND((0.20 * 1 + 0.20 * 1 + 0.15 * IF(n % 5 = 0, 0, 1) + 0.15 * 1
            + 0.10 * 1 + 0.10 * IF(n % 7 = 0, 0, 1) + 0.05 * 1 + 0.05 * IF(n % 11 = 0, 0, 1)) * 5, 2),
       'W9 核验通过', NOW()
FROM seq;

-- ---------------------------------------------------------------------
-- 7. 账单 90 条（租约 1..30 × 最近 3 个月）+ 明细 360 条
--    1..10 已缴、11..20 待确认、21..30 未缴/逾期
-- ---------------------------------------------------------------------
INSERT INTO hrent_rent
 (id, renting_id, period, amount_fen, paid_fen, payable_time, pay_time, tenant_id, state, bill_status,
  channel, voucher_pic, confirm_at, version, create_by, create_time, update_by, update_time, del_flag)
SELECT CONCAT('W9R', LPAD(g.n, 3, '0'), '-', m.m),
       CONCAT('W9G', LPAD(g.n, 3, '0')),
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
FROM (SELECT g.*, CAST(SUBSTRING(g.id, 4) AS UNSIGNED) AS n
      FROM hrent_house_renting g
      WHERE g.id LIKE 'W9G%' AND CAST(SUBSTRING(g.id, 4) AS UNSIGNED) <= 30) g
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
WHERE r.id LIKE 'W9R%';

-- ---------------------------------------------------------------------
-- 8. 刷新评分汇总（与 RatingService 的贝叶斯公式一致：3 条先验 4.0）
-- ---------------------------------------------------------------------
INSERT INTO rating_summary (target_type, target_id, official_score, user_score, display_score, review_count)
SELECT t.target_type, t.target_id, NULL,
       ROUND(AVG(t.score), 2),
       ROUND((3 * 4.0 + SUM(t.score)) / (3 + COUNT(*)), 2),
       COUNT(*)
FROM review t
WHERE t.del_flag = 0 AND t.status = 'PUBLISHED' AND t.id LIKE 'W9V%'
GROUP BY t.target_type, t.target_id
ON DUPLICATE KEY UPDATE
    user_score = VALUES(user_score), display_score = VALUES(display_score), review_count = VALUES(review_count);

INSERT INTO rating_summary (target_type, target_id, official_score, display_score, review_count)
SELECT 'HOUSE', a.house_id, a.score, 4.00, 0
FROM house_audit a
WHERE a.id LIKE 'W9A%'
ON DUPLICATE KEY UPDATE official_score = VALUES(official_score);

-- ---------------------------------------------------------------------
-- 9. 自检（应输出 200 / 52 / 50 / 90 / 360）
-- ---------------------------------------------------------------------
SELECT '房源' AS 对象, COUNT(*) AS 条数 FROM hrent_house_resource WHERE id LIKE 'W9H%'
UNION ALL SELECT '租约', COUNT(*) FROM hrent_house_renting WHERE id LIKE 'W9G%'
UNION ALL SELECT '评价', COUNT(*) FROM review WHERE id LIKE 'W9V%'
UNION ALL SELECT '账单', COUNT(*) FROM hrent_rent WHERE id LIKE 'W9R%'
UNION ALL SELECT '账单明细', COUNT(*) FROM bill_item WHERE id LIKE 'W9R%';
