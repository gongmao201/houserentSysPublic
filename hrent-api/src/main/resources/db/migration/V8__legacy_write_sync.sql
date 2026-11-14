-- =====================================================================
-- V8 · 存量写入路径同步（触发器兜底）
--
-- 背景：V2/V5 采用"只加不删"，老 14 个 Controller 仍只写元字段
--       （money / deposit / start_time / end_time / payable_time）。
--       实测：17 条存量 INSERT 全部能执行（不撞非空约束），但新字段
--       money_fen / deposit_fen / start_date / end_date / amount_fen 全为 NULL，
--       三方对账 2.x 立刻失败，新账单页也看不到老路径写入的账单。
--
-- 方案：BEFORE INSERT/UPDATE 触发器，只在"新字段为空"时按老字段补齐。
--       新代码显式写入的值不会被覆盖（COALESCE 语义），新旧路径可共存。
--       每个触发器体只有一条 SET 单赋值语句，且整条语句写在一行：
--       MySQL 客户端会把跨行的触发器体误判为不完整语句（ERROR 1064），
--       单行写法可同时被 mysql CLI 与 Flyway 正确解析。
-- =====================================================================

SET NAMES utf8mb4;

-- 8.1 房源租金：money(元) → money_fen(分)
DROP TRIGGER IF EXISTS trg_house_resource_bi;
CREATE TRIGGER trg_house_resource_bi BEFORE INSERT ON hrent_house_resource FOR EACH ROW SET NEW.money_fen = COALESCE(NEW.money_fen, ROUND(NEW.money * 100));

DROP TRIGGER IF EXISTS trg_house_resource_bu;
CREATE TRIGGER trg_house_resource_bu BEFORE UPDATE ON hrent_house_resource FOR EACH ROW SET NEW.money_fen = COALESCE(NEW.money_fen, ROUND(NEW.money * 100));

-- 8.2 租房申请押金：deposit(元) → deposit_fen(分)
DROP TRIGGER IF EXISTS trg_apply_rent_bi;
CREATE TRIGGER trg_apply_rent_bi BEFORE INSERT ON hrent_apply_rent FOR EACH ROW SET NEW.deposit_fen = COALESCE(NEW.deposit_fen, ROUND(NEW.deposit * 100));

DROP TRIGGER IF EXISTS trg_apply_rent_bu;
CREATE TRIGGER trg_apply_rent_bu BEFORE UPDATE ON hrent_apply_rent FOR EACH ROW SET NEW.deposit_fen = COALESCE(NEW.deposit_fen, ROUND(NEW.deposit * 100));

-- 8.3 租约租金：money → money_fen
DROP TRIGGER IF EXISTS trg_renting_money_bi;
CREATE TRIGGER trg_renting_money_bi BEFORE INSERT ON hrent_house_renting FOR EACH ROW SET NEW.money_fen = COALESCE(NEW.money_fen, ROUND(NEW.money * 100));

DROP TRIGGER IF EXISTS trg_renting_money_bu;
CREATE TRIGGER trg_renting_money_bu BEFORE UPDATE ON hrent_house_renting FOR EACH ROW SET NEW.money_fen = COALESCE(NEW.money_fen, ROUND(NEW.money * 100));

-- 8.3.1 起租日：'yyyy-MM-dd HH:mm:ss' / 'yyyy-MM-dd' / 'yyyy/MM/dd' → DATE
DROP TRIGGER IF EXISTS trg_renting_start_bi;
CREATE TRIGGER trg_renting_start_bi BEFORE INSERT ON hrent_house_renting FOR EACH ROW SET NEW.start_date = COALESCE(NEW.start_date, DATE(COALESCE(STR_TO_DATE(NEW.start_time, '%Y-%m-%d %H:%i:%s'), STR_TO_DATE(NEW.start_time, '%Y-%m-%d'), STR_TO_DATE(NEW.start_time, '%Y/%m/%d'))));

DROP TRIGGER IF EXISTS trg_renting_start_bu;
CREATE TRIGGER trg_renting_start_bu BEFORE UPDATE ON hrent_house_renting FOR EACH ROW SET NEW.start_date = COALESCE(NEW.start_date, DATE(COALESCE(STR_TO_DATE(NEW.start_time, '%Y-%m-%d %H:%i:%s'), STR_TO_DATE(NEW.start_time, '%Y-%m-%d'), STR_TO_DATE(NEW.start_time, '%Y/%m/%d'))));

-- 8.3.2 到期日
DROP TRIGGER IF EXISTS trg_renting_end_bi;
CREATE TRIGGER trg_renting_end_bi BEFORE INSERT ON hrent_house_renting FOR EACH ROW SET NEW.end_date = COALESCE(NEW.end_date, DATE(COALESCE(STR_TO_DATE(NEW.end_time, '%Y-%m-%d %H:%i:%s'), STR_TO_DATE(NEW.end_time, '%Y-%m-%d'), STR_TO_DATE(NEW.end_time, '%Y/%m/%d'))));

DROP TRIGGER IF EXISTS trg_renting_end_bu;
CREATE TRIGGER trg_renting_end_bu BEFORE UPDATE ON hrent_house_renting FOR EACH ROW SET NEW.end_date = COALESCE(NEW.end_date, DATE(COALESCE(STR_TO_DATE(NEW.end_time, '%Y-%m-%d %H:%i:%s'), STR_TO_DATE(NEW.end_time, '%Y-%m-%d'), STR_TO_DATE(NEW.end_time, '%Y/%m/%d'))));

-- 8.3.3 过期状态：老路径插入一条已过期的租约，不能一直停在"生效中"
--       （排在 end_date 触发器之后创建，读的是已补齐的 NEW.end_date）
DROP TRIGGER IF EXISTS trg_renting_status_bi;
CREATE TRIGGER trg_renting_status_bi BEFORE INSERT ON hrent_house_renting FOR EACH ROW SET NEW.status = CASE WHEN NEW.status = 0 AND NEW.end_date IS NOT NULL AND NEW.end_date < CURDATE() THEN 1 ELSE NEW.status END;

-- 8.4 账单：老 rent 只写 payable_time / state
--     账期取 YYYY-MM；应收取租约 money_fen；state=2 视为已收
--     仅 INSERT 兜底：UPDATE 由新账单模块显式维护 amount_fen，不能被旧值盖回
DROP TRIGGER IF EXISTS trg_rent_period_bi;
CREATE TRIGGER trg_rent_period_bi BEFORE INSERT ON hrent_rent FOR EACH ROW SET NEW.period = COALESCE(NEW.period, DATE_FORMAT(NEW.payable_time, '%Y-%m'));

DROP TRIGGER IF EXISTS trg_rent_amount_bi;
CREATE TRIGGER trg_rent_amount_bi BEFORE INSERT ON hrent_rent FOR EACH ROW SET NEW.amount_fen = COALESCE(NEW.amount_fen, (SELECT g.money_fen FROM hrent_house_renting g WHERE g.id = NEW.renting_id));

DROP TRIGGER IF EXISTS trg_rent_paid_bi;
CREATE TRIGGER trg_rent_paid_bi BEFORE INSERT ON hrent_rent FOR EACH ROW SET NEW.paid_fen = CASE WHEN NEW.state = 2 THEN COALESCE(NEW.amount_fen, (SELECT g.money_fen FROM hrent_house_renting g WHERE g.id = NEW.renting_id), 0) ELSE COALESCE(NEW.paid_fen, 0) END;

-- 8.5 迁移断言：触发器不是"建了一半"
DROP TABLE IF EXISTS _v8_assert;
CREATE TABLE _v8_assert (
    id       INT PRIMARY KEY AUTO_INCREMENT,
    rule     VARCHAR(64) NOT NULL,
    bad_rows BIGINT      NOT NULL,
    CONSTRAINT ck_v8_no_bad_rows CHECK (bad_rows = 0)
) ENGINE = InnoDB;

INSERT INTO _v8_assert (rule, bad_rows)
SELECT '触发器缺失', 14 - (
    SELECT COUNT(*) FROM information_schema.triggers
    WHERE trigger_schema = DATABASE()
      AND trigger_name IN ('trg_house_resource_bi', 'trg_house_resource_bu',
                           'trg_apply_rent_bi', 'trg_apply_rent_bu',
                           'trg_renting_money_bi', 'trg_renting_money_bu',
                           'trg_renting_start_bi', 'trg_renting_start_bu',
                           'trg_renting_end_bi', 'trg_renting_end_bu',
                           'trg_renting_status_bi',
                           'trg_rent_period_bi', 'trg_rent_amount_bi', 'trg_rent_paid_bi'));
DROP TABLE _v8_assert;
