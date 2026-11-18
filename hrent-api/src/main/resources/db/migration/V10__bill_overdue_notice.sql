-- =====================================================================
-- V10 · 账单逾期站内信标记
-- 背景：W7 交付「按月生成 + 逾期站内信」。定时任务每天扫描到期未缴账单，
--       落库 OVERDUE 并给租客发 sys_message；用 overdue_notified_at 做发送幂等，
--       多实例并发时只有一个 UPDATE 能命中，避免重复轰炸。
-- =====================================================================

SET NAMES utf8mb4;

ALTER TABLE hrent_rent
    ADD COLUMN overdue_notified_at DATETIME DEFAULT NULL COMMENT '逾期站内信发送时间（幂等标记）' AFTER confirm_at;

ALTER TABLE hrent_rent
    ADD KEY idx_overdue_notify (bill_status, payable_time, overdue_notified_at);

-- 迁移断言：列与索引必须存在，否则后续定时任务会静默失效
DROP TABLE IF EXISTS _v10_assert;
CREATE TABLE _v10_assert (
    id       INT PRIMARY KEY AUTO_INCREMENT,
    rule     VARCHAR(64) NOT NULL,
    bad_rows BIGINT      NOT NULL,
    CONSTRAINT ck_v10_no_bad CHECK (bad_rows = 0)
) ENGINE = InnoDB;

INSERT INTO _v10_assert (rule, bad_rows)
SELECT 'overdue_notified_at 列缺失', CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'hrent_rent' AND column_name = 'overdue_notified_at';

INSERT INTO _v10_assert (rule, bad_rows)
SELECT 'idx_overdue_notify 索引缺失', CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'hrent_rent' AND index_name = 'idx_overdue_notify';

DROP TABLE _v10_assert;
