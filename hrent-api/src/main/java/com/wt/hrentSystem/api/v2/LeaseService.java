package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.api.dao.V2Mapper;
import com.wt.hrentSystem.common.entity.User;
import com.wt.hrentSystem.lease.dao.LeaseLogDao;
import com.wt.hrentSystem.lease.entity.LeaseLog;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 租约 v2（docs/13 §4.3）。
 * 关键：DB 的 status 是 TINYINT 0/1/2/3，接口契约是字符串枚举，映射只在本类做（待定 T-02）。
 */
@Service
public class LeaseService {

    /**ACTIVE / EXPIRED / TERMINATED / CANCELLED 与 V2 迁移的 0/1/2/3 对应 */
    private static final String[] STATUS_NAMES = {"ACTIVE", "EXPIRED", "TERMINATED", "CANCELLED"};

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

    public Map<String, Object> terminate(String id, Map<String, Object> body, User user) {
        Req.body(body);
        String endDate = Req.str(body, "actualEndDate", true, 10);
        // 用标准解析校验日期，避免 '2026-13-45' 这类值直接进 SQL
        Date parsed = Req.date(body, "actualEndDate", true);
        String reason = Req.str(body, "reason", false, 255);

        Map<String, Object> row = v2Mapper.getLeaseById(id);
        if (row == null) {
            throw ApiException.notFound("租约不存在");
        }
        assertCanAccess(user, row);
        int current = Rows.intOr(row.get("statusCode"), -1);
        if (current != 0) {
            throw ApiException.conflict("当前租约状态不允许退租确认（" + statusName(current) + "）");
        }
        int version = Rows.intOr(row.get("version"), 0);
        int affected = v2Mapper.terminateLease(id, V2Time.day(parsed), version);
        if (affected == 0) {
            throw ApiException.conflict("租约已被其他操作更新，请刷新后重试");
        }

        LeaseLog log = new LeaseLog();
        log.setId(UUID.randomUUID().toString().replace("-", ""));
        log.setLeaseId(id);
        log.setFromStatus(0);
        log.setToStatus(2);
        log.setOperatorId(user.getId());
        log.setRemark(reason);
        log.setCreateTime(new Date());
        leaseLogDao.insert(log);

        LocalDate windowEnd = LocalDate.parse(endDate, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE).plusDays(30);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leaseId", id);
        result.put("status", "TERMINATED");
        result.put("actualEndDate", endDate);
        result.put("reviewWindowUntil", windowEnd.toString());
        return result;
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
        vo.put("status", statusName(Rows.intOr(row.get("statusCode"), -1)));
        vo.remove("statusCode");
        vo.put("startDate", Rows.day(row.get("startDate")));
        vo.put("endDate", Rows.day(row.get("endDate")));
        vo.put("actualEndDate", Rows.day(row.get("actualEndDate")));
        vo.put("moneyFen", Rows.longOr(row.get("moneyFen"), 0L));
        return vo;
    }
}
