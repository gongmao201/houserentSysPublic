package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.api.dao.V2Mapper;
import com.wt.hrentSystem.common.entity.User;
import com.wt.hrentSystem.lease.dao.LeaseLogDao;
import com.wt.hrentSystem.lease.entity.LeaseLog;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 租约 v2（docs/13 §4.3 / docs/14 FR-LEASE-02、FR-LEASE-03）。
 *
 * 状态机（DB 为 TINYINT）：
 *   0 生效中 → 1 已到期 / 2 已退租 / 3 已解约
 *   1 已到期 → 2 已退租（补做退租确认）
 *   2 已退租、3 已解约 为终态，任何流转都拒绝（409）
 * 每次流转写 lease_log，并用 version 乐观锁；重复点击只会有一次成功，其余 409。
 */
@Service
public class LeaseService {

    /**ACTIVE / EXPIRED / TERMINATED / CANCELLED 与 V2 迁移的 0/1/2/3 对应 */
    private static final String[] STATUS_NAMES = {"ACTIVE", "EXPIRED", "TERMINATED", "CANCELLED"};

    private static final int ACTIVE = 0;
    private static final int EXPIRED = 1;
    private static final int TERMINATED = 2;
    private static final int CANCELLED = 3;

    /**合法迁移表：key=当前状态，value=允许到达的状态集合 */
    private static final Map<Integer, Set<Integer>> TRANSITIONS;

    static {
        Map<Integer, Set<Integer>> map = new LinkedHashMap<>();
        map.put(ACTIVE, new LinkedHashSet<>(Arrays.asList(EXPIRED, TERMINATED, CANCELLED)));
        map.put(EXPIRED, new LinkedHashSet<>(Collections.singletonList(TERMINATED)));
        map.put(TERMINATED, Collections.emptySet());
        map.put(CANCELLED, Collections.emptySet());
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    @Resource
    private V2Mapper v2Mapper;

    @Resource
    private LeaseLogDao leaseLogDao;

    @Resource
    private V2Context v2Context;

    public static String statusName(int code) {
        return code >= 0 && code < STATUS_NAMES.length ? STATUS_NAMES[code] : "UNKNOWN";
    }

    public static Integer statusCode(String name) {
        if (name == null) {
            return null;
        }
        for (int i = 0; i < STATUS_NAMES.length; i++) {
            if (STATUS_NAMES[i].equals(name)) {
                return i;
            }
        }
        throw ApiException.bad("status 取值不合法，允许：" + String.join("/", STATUS_NAMES));
    }

    /**状态机唯一判定入口：白名单外的组合一律非法 */
    public static boolean canTransit(int from, int to) {
        Set<Integer> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**当前状态允许去的目标状态名，用于 409 响应与前端置灰 */
    public static List<String> allowedNext(int from) {
        List<String> names = new ArrayList<>();
        Set<Integer> allowed = TRANSITIONS.get(from);
        if (allowed != null) {
            for (Integer code : allowed) {
                names.add(statusName(code));
            }
        }
        return names;
    }

    public Map<String, Object> get(String id, User user) {
        Map<String, Object> row = v2Mapper.getLeaseById(id);
        if (row == null) {
            throw ApiException.notFound("租约不存在");
        }
        assertCanAccess(user, row);
        return toVo(row);
    }

    public PageResult<Map<String, Object>> mine(User user, String status, int pageNo, int pageSize) {
        Integer code = statusCode(status);
        List<Map<String, Object>> rows = v2Mapper.findMyLeases(user.getId(), code, v2Context.isAdmin(user));
        List<Map<String, Object>> all = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                all.add(toVo(row));
            }
        }
        // 租约量级小（种子数据 30 条），SQL 已 LIMIT 200；此处内存分页保证 count 精确。
        int from = Math.min((pageNo - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return PageResult.of(pageNo, pageSize, all.size(), new ArrayList<>(all.subList(from, to)));
    }

    /**
     * 退租确认（FR-LEASE-03）：租客或房东任一方可发起。
     * 生效中 → 已退租；已到期 → 已退租（补做确认，避免到期后评价窗口永远打不开）。
     */
    public Map<String, Object> terminate(String id, Map<String, Object> body, User user) {
        Req.body(body);
        String endDate = Req.str(body, "actualEndDate", true, 10);
        // 用标准解析校验日期，避免 '2026-13-45' 这类值直接进 SQL
        Date parsed = Req.date(body, "actualEndDate", true);
        String reason = Req.str(body, "reason", false, 255);

        Map<String, Object> row = requireLease(id);
        assertCanAccess(user, row);
        int from = Rows.intOr(row.get("statusCode"), -1);
        assertTransit(from, TERMINATED);
        transition(id, row, from, TERMINATED, V2Time.day(parsed), reason, user);

        LocalDate windowEnd = LocalDate.parse(endDate, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE).plusDays(30);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaseId", id);
        result.put("status", "TERMINATED");
        result.put("actualEndDate", endDate);
        result.put("reviewWindowUntil", windowEnd.toString());
        result.put("allowedNext", allowedNext(TERMINATED));
        return result;
    }

    /**
     * 到期（0 → 1）：房东或管理员触发；租客无权限把租约置为到期。
     * 典型调用方是定时任务，人工补录也应走这里，保证 lease_log 有痕迹。
     */
    public Map<String, Object> expire(String id, Map<String, Object> body, User user) {
        if (body != null) {
            Req.body(body);
        }
        Map<String, Object> row = requireLease(id);
        String landlordId = Rows.str(row.get("landlordId"));
        if (!v2Context.isAdmin(user) && !user.getId().equals(landlordId)) {
            throw ApiException.forbidden("仅房东或管理员可标记到期");
        }
        int from = Rows.intOr(row.get("statusCode"), -1);
        assertTransit(from, EXPIRED);
        String remark = body == null ? null : Req.str(body, "reason", false, 255);
        transition(id, row, from, EXPIRED, null, remark, user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaseId", id);
        result.put("status", "EXPIRED");
        result.put("allowedNext", allowedNext(EXPIRED));
        return result;
    }

    /**
     * 解约（0 → 3）：租客或房东任一方；必须写原因，便于申诉与对账。
     */
    public Map<String, Object> cancel(String id, Map<String, Object> body, User user) {
        Req.body(body);
        String reason = Req.str(body, "reason", true, 255);
        Map<String, Object> row = requireLease(id);
        assertCanAccess(user, row);
        int from = Rows.intOr(row.get("statusCode"), -1);
        assertTransit(from, CANCELLED);
        transition(id, row, from, CANCELLED, null, reason, user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaseId", id);
        result.put("status", "CANCELLED");
        result.put("reason", reason);
        result.put("allowedNext", allowedNext(CANCELLED));
        return result;
    }

    /**租约流转公共尾巴：乐观锁更新 + 写 lease_log；影响行数 0 一律 409 */
    private void transition(String id, Map<String, Object> row, int from, int to,
                            String actualEndDate, String remark, User user) {
        int version = Rows.intOr(row.get("version"), 0);
        int affected = v2Mapper.transitionLease(id, from, to, actualEndDate, version);
        if (affected == 0) {
            throw ApiException.conflict("租约已被其他操作更新，请刷新后重试");
        }
        LeaseLog log = new LeaseLog();
        log.setId(UUID.randomUUID().toString().replace("-", ""));
        log.setLeaseId(id);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(user.getId());
        log.setRemark(remark);
        log.setCreateTime(new Date());
        leaseLogDao.insert(log);
    }

    /**非法迁移：409，并回传当前状态与允许去向（docs/13 §5 第 5 条） */
    private void assertTransit(int from, int to) {
        if (from == to) {
            throw ApiException.conflict("租约已是 " + statusName(from) + "，重复操作不再受理");
        }
        if (!canTransit(from, to)) {
            java.util.List<String> allowed = allowedNext(from);
            throw ApiException.conflict("状态流转非法：" + statusName(from) + " → " + statusName(to)
                    + (allowed.isEmpty() ? "，该状态为终态，不可再流转"
                    : "，允许：" + String.join("/", allowed)));
        }
    }

    private Map<String, Object> requireLease(String id) {
        Map<String, Object> row = v2Mapper.getLeaseById(id);
        if (row == null) {
            throw ApiException.notFound("租约不存在");
        }
        return row;
    }

    /**租客本人 / 房源所属房东 / 管理员可访问；其余 403（不返回 404，避免猜 ID 探测） */
    private void assertCanAccess(User user, Map<String, Object> row) {
        if (v2Context.isAdmin(user)) {
            return;
        }
        String tenantId = Rows.str(row.get("tenantId"));
        String landlordId = Rows.str(row.get("landlordId"));
        if (user.getId().equals(tenantId) || user.getId().equals(landlordId)) {
            return;
        }
        throw ApiException.forbidden("无权操作该租约");
    }

    private Map<String, Object> toVo(Map<String, Object> row) {
        Map<String, Object> vo = Rows.copy(row);
        int code = Rows.intOr(row.get("statusCode"), -1);
        vo.put("status", statusName(code));
        vo.put("allowedNext", allowedNext(code));
        vo.remove("statusCode");
        vo.put("startDate", Rows.day(row.get("startDate")));
        vo.put("endDate", Rows.day(row.get("endDate")));
        vo.put("actualEndDate", Rows.day(row.get("actualEndDate")));
        vo.put("moneyFen", Rows.longOr(row.get("moneyFen"), 0L));
        return vo;
    }
}
