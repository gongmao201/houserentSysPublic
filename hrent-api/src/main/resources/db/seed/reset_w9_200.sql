-- W9 种子数据清理（只删 W9 前缀，不碰 admin 与真实业务数据）
SET NAMES utf8mb4;
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

SELECT 'W9 种子已清理' AS 结果;
