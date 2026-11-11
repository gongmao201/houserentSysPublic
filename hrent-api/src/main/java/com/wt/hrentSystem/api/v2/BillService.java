package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.api.dao.V2Mapper;
import com.wt.hrentSystem.bill.dao.BillItemDao;
import com.wt.hrentSystem.bill.entity.BillItem;
import com.wt.hrentSystem.common.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

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

    public Map<String, Object> generate(Map<String, Object> body, User user) {
        Req.body(body);
        String rentingId = Req.str(body, "rentingId", true, 64);
        String period = Req.str(body, "period", true, 7);
        try {
            YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw ApiException.bad("period 必须是 YYYY-MM");
        }
        if (v2Mapper.findBillIdByRentingPeriod(rentingId, period) != null) {
            throw ApiException.conflict("该租约该账期已生成账单");
        }
        Map<String, Object> renting = v2Mapper.getRentingBrief(rentingId);
        if (renting == null) {
            throw ApiException.notFound("租约不存在");
        }
        v2Context.assertOwner(user, Rows.str(renting.get("landlordId")));
        Long moneyFen = Rows.lng(renting.get("moneyFen"));
        if (moneyFen == null || moneyFen <= 0) {
            throw ApiException.unprocessable("租约未回填月租(分)，无法生成账单");
        }
        String tenantId = Rows.str(renting.get("tenantId"));
        String billId = UUID.randomUUID().toString().replace("-", "");
        try {
            v2Mapper.insertBill(billId, rentingId, period, moneyFen,
                    period + "-05", tenantId);
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
        result.put("period", period);
        result.put("amountFen", moneyFen);
        result.put("status", "UNPAID");
        result.put("payableDate", period + "-05");
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
