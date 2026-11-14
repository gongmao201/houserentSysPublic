-- =====================================================================
-- V9 · 字符集 / 时区固化（并复核新表 DDL 是否齐备）
--
-- 背景：
--   1) MySQL 8 建库默认排序规则是 utf8mb4_0900_ai_ci，而全部业务表实际是
--      utf8mb4_general_ci。库默认与表默认不一致时，手工 CREATE TABLE 会再冒出
--      0900 表，跨排序规则 JOIN 直接报 "Illegal mix of collations"。
--   2) 会话时区原来是 SYSTEM，取决于 MySQL 服务器/操作系统；JDBC 虽带了
--      serverTimezone=Asia/Shanghai，但库侧 NOW() 与 Java 侧仍可能差 8 小时。
--
-- 本脚本做两件事：
--   A. 把库默认排序规则统一成 utf8mb4_general_ci，并把 25 张业务表
--      CONVERT 到同一字符集/排序规则（已一致时为空操作，只是重建一遍）
--   B. 用 _v9_assert 做硬校验：库默认、表排序规则、新表是否存在，有一条不满足
--      就让 Flyway 直接失败，而不是"看起来迁移成功"
--
-- 时区的运行时固定放在两处（DDL 无法全局改）：
--   - 连接池：spring.datasource.hikari.connection-init-sql = SET time_zone='+08:00'
--   - JVM：scripts/hrent.ps1 的 -Duser.timezone=Asia/Shanghai
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- A. 库级默认（对后续 CREATE TABLE 生效）
-- ---------------------------------------------------------------------
ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- A. 表级统一（25 张业务表，不含 flyway_schema_history）
-- ---------------------------------------------------------------------
ALTER TABLE ai_feedback             CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE bill_item               CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE house_audit             CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE house_embedding         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE hrent_apply_rent        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE hrent_apply_view        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE hrent_collection        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE hrent_house_renting     CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE hrent_house_resource    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE hrent_pic               CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE hrent_rent              CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE hrent_tag               CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE hrent_tag_and_house     CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE lease_log               CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE rating_summary          CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE repair_image            CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE repair_log              CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE repair_order            CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE review                  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE review_appeal           CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE sys_dict                CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE sys_menu                CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE sys_message             CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE sys_user                CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE user_role               CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- B. 硬校验（bad_rows 必须为 0，否则 CHECK 约束让迁移中断）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS _v9_assert;
CREATE TABLE _v9_assert (
    id       INT PRIMARY KEY AUTO_INCREMENT,
    rule     VARCHAR(80) NOT NULL,
    bad_rows BIGINT      NOT NULL,
    CONSTRAINT ck_v9_no_bad CHECK (bad_rows = 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- B1. 库默认排序规则不是 utf8mb4_general_ci → 1 条坏记录
--     用 information_schema.schemata 判断：@@collation_database 在部分客户端/连接上
--     不会随 ALTER DATABASE 刷新，会误报。
INSERT INTO _v9_assert (rule, bad_rows)
SELECT '数据库默认排序规则未统一',
       COUNT(*)
FROM information_schema.schemata
WHERE schema_name = DATABASE()
  AND default_collation_name <> 'utf8mb4_general_ci';

-- B2. 仍有业务表不是 utf8mb4_general_ci → 按表计数
INSERT INTO _v9_assert (rule, bad_rows)
SELECT '业务表排序规则未统一',
       COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name <> 'flyway_schema_history'
  AND table_name NOT LIKE '\_v9\_assert'
  AND table_collation <> 'utf8mb4_general_ci';

-- B3. 新表齐备性：rating / audit / review / repair_* / bill_item 共 8 张
INSERT INTO _v9_assert (rule, bad_rows)
SELECT '新模块表缺失',
       8 - COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('house_audit', 'review', 'review_appeal', 'rating_summary',
                     'repair_order', 'repair_image', 'repair_log', 'bill_item');

DROP TABLE _v9_assert;
