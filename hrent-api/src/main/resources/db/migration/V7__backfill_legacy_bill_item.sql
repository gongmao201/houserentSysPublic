-- =====================================================================
-- V7 · 存量账单补明细
-- =====================================================================
-- 背景：V5 把 hrent_rent.amount_fen 从租约回填了，但没有生成 bill_item；
--       verify.sql 3.1/3.2 会各报 1 条（存量账单无明细）。
-- 做法：只给"有应收金额、但一条明细都没有"的账单补一条 RENT 明细。
-- 幂等：NOT EXISTS，重复执行不会重复插入。
-- 注意：不改 V5 的历史 checksum；V5 已在生产执行过。
-- =====================================================================

SET NAMES utf8mb4;

INSERT INTO bill_item (id, rent_id, item_type, amount_fen, remark, sort, create_time)
SELECT LEFT(REPLACE(UUID(), '-', ''), 32) AS id,
       r.id                               AS rent_id,
       'RENT'                             AS item_type,
       r.amount_fen                       AS amount_fen,
       '存量租金记录迁移'                  AS remark,
       0                                  AS sort,
       NOW()                              AS create_time
FROM hrent_rent r
WHERE r.amount_fen IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM bill_item i WHERE i.rent_id = r.id
  );

-- 校验：不应再有"有金额无明细"的账单
-- SELECT COUNT(*) FROM hrent_rent r
-- LEFT JOIN bill_item i ON i.rent_id = r.id
-- WHERE r.amount_fen IS NOT NULL AND i.id IS NULL;
