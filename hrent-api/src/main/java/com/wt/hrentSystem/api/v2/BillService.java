package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.api.dao.V2Mapper;
import com.wt.hrentSystem.bill.dao.BillItemDao;
import com.wt.hrentSystem.bill.entity.BillItem;
import com.wt.hrentSystem.common.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 账单 v2（docs/13 §4.5）。本期不含在线支付：租客线下转账后标记 + 房东确认到账。
 * 金额一律 long「分」；生成时同步写一条 RENT 明细，保证 SUM(items) == amount_fen。
 * W7 增加：按月批量生成（定时任务调用）与逾期扫描（写 sys_message 站内信）。
 */
@Service
public class BillService {

    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    @Resource
    private V2Mapper v2Mapper;
    @Resource
    private BillItemDao billItemDao;
    @Resource
    private V2Context v2Context;
    @Resource
    private BillNoticeService billNoticeService;

    public Map<String, Object> generate(Map<String, Object> body, User user) {
        Req.body(body);
        String rentingId = Req.str(body, "rentingId", true, 64);
        String period = Req.str(body, "period", true, 7);
        YearMonth ym = parsePeriod(period);
        Map<String, Object> renting = v2Mapper.getRentingBrief(rentingId);
        if (renting == null) {
            throw ApiException.notFound("租约不存在");
        }
        v2Context.assertOwner(user, Rows.str(renting.get("landlordId")));
        return createBill(renting, ym.toString(), user.getId());
    }

    /**
     * 按月批量生成：只处理「当前生效 + 覆盖该账期 + 该账期未建账单」的租约。
     * 幂等由 uk_renting_period 唯一键兜底；并发/重复执行只记 skipped，不报错。
     */
    public Map<String, Object> monthlyGenerate(YearMonth period, String operatorId) {
        String p = period.toString();
        String start = period.atDay(1).toString();
        String end = period.atEndOfMonth().toString();
        List<Map<String, Object>> rentings = v2Mapper.findRentingsForMonthlyBill(p, start, end);
        int generated = 0;
        int skipped = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        if (rentings != null) {
            for (Map<String, Object> renting : rentings) {
                try {
                    createBill(renting, p, operatorId);
                    generated++;
                } catch (DuplicateKeyException e) {
                    skipped++;
                } catch (ApiException e) {
                    if (e.getCode() == ApiCodes.CONFLICT) {
                        //并发/重复执行：账期已被别人建过，跳过即可
                        skipped++;
                    } else {
                        failed++;
                        String rentingId = Rows.str(renting.get("rentingId"));
                        if (failures.size() < 5) {
                            failures.add(rentingId + ": " + e.getMessage());
                        }
                        log.error("按月生成账单失败 rentingId=" + rentingId + " period=" + p, e);
                    }
                } catch (Exception e) {
                    failed++;
                    String rentingId = Rows.str(renting.get("rentingId"));
                    if (failures.size() < 5) {
                        failures.add(rentingId + ": " + e.getMessage());
                    }
                    log.error("按月生成账单失败 rentingId=" + rentingId + " period=" + p, e);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", p);
        result.put("candidates", rentings == null ? 0 : rentings.size());
        result.put("generated", generated);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("failures", failures);
        return result;
    }

    /**逾期扫描：逐条落 OVERDUE + 发站内信；mark 条件更新保证多实例只发一次 */
    public Map<String, Object> scanOverdue(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> candidates = v2Mapper.findOverdueBillCandidates(safeLimit);
        int notified = 0;
        int skipped = 0;
        int failed = 0;
        if (candidates != null) {
            for (Map<String, Object> row : candidates) {
                try {
                    if (billNoticeService.notifyOverdue(row)) {
                        notified++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("逾期站内信发送失败 billId=" + Rows.str(row.get("id")), e);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidates", candidates == null ? 0 : candidates.size());
        result.put("notified", notified);
        result.put("skipped", skipped);
        result.put("failed", failed);
        return result;
    }

    private YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw ApiException.bad("period 必须是 YYYY-MM");
        }
    }

    /**单条建账：重复账期 409；金额取租约月租，同步写 RENT 明细 */
    private Map<String, Object> createBill(Map<String, Object> renting, String period, String operatorId) {
        String rentingId = Rows.str(renting.get("rentingId"));
        if (v2Mapper.findBillIdByRentingPeriod(rentingId, period) != null) {
            throw ApiException.conflict("该租约该账期已生成账单");
        }
        Long moneyFen = Rows.lng(renting.get("moneyFen"));
        if (moneyFen == null || moneyFen <= 0) {
            throw ApiException.unprocessable("租约未回填月租(分)，无法生成账单");
        }
        String tenantId = Rows.str(renting.get("tenantId"));
        String billId = UUID.randomUUID().toString().replace("-", "");
        String payableDate = period + "-05";
        try {
            v2Mapper.insertBill(billId, rentingId, period, moneyFen,
                    payableDate, tenantId, operatorId);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("该租约该账期已生成账单");
        }
        BillItem item = new BillItem();
        item.setId(UUID.randomUUID().toString().replace("-", ""));
        item.setRentId(billId);
        item.setItemType("RENT");
        item.setAmountFen(moneyFen);
        item.setRemark("月租");
        item.setSort(0);
        item.setCreateTime(new Date());
        billItemDao.insert(item);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billId", billId);
        result.put("rentingId", rentingId);
        result.put("period", period);
        result.put("amountFen", moneyFen);
        result.put("status", "UNPAID");
        result.put("payableDate", payableDate);
        return result;
    }

    /**
     * 追加费用明细（水/电/物业/其他），同步抬高应收。
     * 只允许房东/管理员在 UNPAID/OVERDUE 阶段操作；金额与明细在同一事务内变更，保持 SUM==amount。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addItem(String id, Map<String, Object> body, User user) {
        Req.body(body);
        String itemType = Req.oneOf(body, "itemType", true, "WATER", "ELECTRIC", "PROPERTY", "OTHER");
        Long amountFen = Req.fen(body, "amountFen", true);
        if (amountFen <= 0) {
            throw ApiException.unprocessable("明细金额必须大于 0");
        }
        String remark = Req.str(body, "remark", false, 200);
        Map<String, Object> row = require(id, user);
        v2Context.assertOwner(user, Rows.str(row.get("landlordId")));
        String status = Rows.str(row.get("billStatus"));
        if (!"UNPAID".equals(status) && !"OVERDUE".equals(status)) {
            throw ApiException.conflict("仅待缴/逾期的账单可追加明细（当前 " + status + "）");
        }
        int affected = v2Mapper.addBillItemAmount(id, amountFen, Rows.intOr(row.get("version"), 0));
        if (affected == 0) {
            throw ApiException.conflict("账单已被其他操作更新，请刷新后重试");
        }
        List<BillItem> existing = billItemDao.findByRentId(id);
        BillItem item = new BillItem();
        item.setId(UUID.randomUUID().toString().replace("-", ""));
        item.setRentId(id);
        item.setItemType(itemType);
        item.setAmountFen(amountFen);
        item.setRemark(remark);
        item.setSort(existing == null ? 0 : existing.size());
        item.setCreateTime(new Date());
        billItemDao.insert(item);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billId", id);
        result.put("itemType", itemType);
        result.put("amountFen", amountFen);
        result.put("totalAmountFen", Rows.longOr(row.get("amountFen"), 0L) + amountFen);
        return result;
    }

    public PageResult<Map<String, Object>> mine(User user, String period, String status,
                                               int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        boolean admin = v2Context.isAdmin(user);
        List<Map<String, Object>> rows = v2Mapper.findMyBills(
                user.getId(), period, status, admin, offset, pageSize);
        int count = v2Mapper.countMyBills(user.getId(), period, status, admin);
        return PageResult.of(pageNo, pageSize, count, toVos(rows));
    }

    public Map<String, Object> get(String id, User user) {
        Map<String, Object> row = require(id, user);
        Map<String, Object> vo = toVo(row);
        List<BillItem> items = billItemDao.findByRentId(id);
        List<Map<String, Object>> itemVos = new ArrayList<>();
        long sum = 0L;
        if (items != null) {
            for (BillItem item : items) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemType", item.getItemType());
                m.put("amountFen", item.getAmountFen());
                m.put("remark", item.getRemark());
                itemVos.add(m);
                sum += item.getAmountFen() == null ? 0L : item.getAmountFen();
            }
        }
        long amount = Rows.longOr(row.get("amountFen"), 0L);
        if (!itemVos.isEmpty() && sum != amount) {
            // 数据错误绝不静默：记日志 + 返回 500，让对账问题立刻暴露
            log.error("账单明细与应收不等 billId=" + id + " amountFen=" + amount + " itemSum=" + sum);
            throw new ApiException(ApiCodes.SERVER_ERROR, "账单数据异常，请联系管理员核对");
        }
        vo.put("items", itemVos);
        return vo;
    }

    public Map<String, Object> markPaid(String id, Map<String, Object> body, User user) {
        Req.body(body);
        Long paidFen = Req.fen(body, "paidFen", true);
        String channel = Req.oneOf(body, "channel", true,
                "offline_wechat", "offline_alipay", "offline_cash");
        String voucherPic = Req.str(body, "voucherPic", false, 300);

        Map<String, Object> row = require(id, user);
        v2Context.assertOwner(user, Rows.str(row.get("tenantId")));
        long amountFen = Rows.longOr(row.get("amountFen"), 0L);
        if (paidFen <= 0 || paidFen > amountFen) {
            throw ApiException.unprocessable("标记金额必须在 0 与应收金额之间");
        }
        String status = Rows.str(row.get("billStatus"));
        if ("MARKED".equals(status) && paidFen == Rows.longOr(row.get("paidFen"), 0L)) {
            return paidResult(id, "MARKED", paidFen, true);
        }
        if (!"UNPAID".equals(status) && !"OVERDUE".equals(status)) {
            throw ApiException.conflict("当前账单状态不允许标记已缴（" + status + "）");
        }
        int affected = v2Mapper.updateBillPaid(id, paidFen, channel, voucherPic,
                Rows.intOr(row.get("version"), 0));
        if (affected == 0) {
            throw ApiException.conflict("账单已被其他操作更新，请刷新后重试");
        }
        return paidResult(id, "MARKED", paidFen, false);
    }

    public Map<String, Object> confirm(String id, User user) {
        Map<String, Object> row = require(id, user);
        v2Context.assertOwner(user, Rows.str(row.get("landlordId")));
        String status = Rows.str(row.get("billStatus"));
        // 重复确认：200 且不累加，满足幂等
        if ("PAID".equals(status)) {
            return paidResult(id, "PAID", Rows.longOr(row.get("paidFen"), 0L), true);
        }
        if (!"MARKED".equals(status)) {
            throw ApiException.conflict("仅「租客已标记」的账单可确认到账（当前 " + status + "）");
        }
        int affected = v2Mapper.confirmBill(id, Rows.intOr(row.get("version"), 0));
        if (affected == 0) {
            throw ApiException.conflict("账单已被其他操作更新，请刷新后重试");
        }
        return paidResult(id, "PAID", Rows.longOr(row.get("paidFen"), 0L), false);
    }

    public PageResult<Map<String, Object>> pending(User user, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        boolean admin = v2Context.isAdmin(user);
        List<Map<String, Object>> rows = v2Mapper.findPendingBills(user.getId(), admin, offset, pageSize);
        int count = v2Mapper.countPendingBills(user.getId(), admin);
        return PageResult.of(pageNo, pageSize, count, toVos(rows));
    }

    private Map<String, Object> require(String id, User user) {
        Map<String, Object> row = v2Mapper.getBillById(id);
        if (row == null) {
            throw ApiException.notFound("账单不存在");
        }
        String tenantId = Rows.str(row.get("tenantId"));
        String landlordId = Rows.str(row.get("landlordId"));
        if (!v2Context.isAdmin(user)
                && !user.getId().equals(tenantId)
                && !user.getId().equals(landlordId)) {
            throw ApiException.forbidden("无权操作该账单");
        }
        return row;
    }

    private List<Map<String, Object>> toVos(List<Map<String, Object>> rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                list.add(toVo(row));
            }
        }
        return list;
    }

    private Map<String, Object> toVo(Map<String, Object> row) {
        Map<String, Object> vo = Rows.copy(row);
        vo.put("payableDate", Rows.day(row.get("payableDate")));
        vo.put("confirmAt", Rows.iso(row.get("confirmAt")));
        vo.put("overdueNotifiedAt", Rows.iso(row.get("overdueNotifiedAt")));
        vo.put("amountFen", Rows.longOr(row.get("amountFen"), 0L));
        vo.put("paidFen", Rows.longOr(row.get("paidFen"), 0L));
        return vo;
    }

    private Map<String, Object> paidResult(String id, String status, long paidFen, boolean idempotent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("billId", id);
        result.put("status", status);
        result.put("paidFen", paidFen);
        result.put("idempotentHit", idempotent);
        return result;
    }
}
