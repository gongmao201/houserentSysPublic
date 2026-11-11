-- =====================================================================
-- 清空开发种子数据（保留表结构与真实数据）
-- 与 seed_dev.sql 顶部的清理段同源，单独抽出便于"演示前 10 秒重置"
--
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 house_rent < reset_dev.sql
-- 安全：只删 id 以 SEED 开头的行，不 TRUNCATE、不 DROP
-- =====================================================================

SET NAMES utf8mb4;

DELETE FROM bill_item            WHERE rent_id   LIKE 'SEEDR%';
DELETE FROM hrent_rent           WHERE id        LIKE 'SEEDR%';
DELETE FROM review_appeal        WHERE review_id LIKE 'SEEDV%';
DELETE FROM review               WHERE id        LIKE 'SEEDV%';
DELETE FROM house_audit          WHERE id        LIKE 'SEEDA%';
DELETE FROM rating_summary       WHERE target_id LIKE 'SEEDH%' OR target_id LIKE 'SEEDL%';
DELETE FROM ai_feedback          WHERE house_id  LIKE 'SEEDH%';
DELETE FROM lease_log            WHERE lease_id  LIKE 'SEEDG%';
DELETE FROM hrent_house_renting  WHERE id        LIKE 'SEEDG%';
DELETE FROM hrent_tag_and_house  WHERE house_id  LIKE 'SEEDH%';
DELETE FROM hrent_pic            WHERE id        LIKE 'SEEDP%';
DELETE FROM hrent_house_resource WHERE id        LIKE 'SEEDH%';
DELETE FROM user_role            WHERE user_id   LIKE 'SEED%';
DELETE FROM sys_user             WHERE id        LIKE 'SEED%';

SELECT '重置完成，剩余种子行（应全为 0）' AS 说明,
       (SELECT COUNT(*) FROM hrent_house_resource WHERE id LIKE 'SEEDH%')
     + (SELECT COUNT(*) FROM hrent_house_renting WHERE id LIKE 'SEEDG%')
     + (SELECT COUNT(*) FROM review             WHERE id LIKE 'SEEDV%')
     + (SELECT COUNT(*) FROM hrent_rent          WHERE id LIKE 'SEEDR%') AS 残留;
