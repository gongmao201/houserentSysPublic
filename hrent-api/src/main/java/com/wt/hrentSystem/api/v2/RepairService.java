package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.api.dao.V2Mapper;
import com.wt.hrentSystem.common.entity.User;
import com.wt.hrentSystem.repair.dao.RepairImageDao;
import com.wt.hrentSystem.repair.dao.RepairLogDao;
import com.wt.hrentSystem.repair.dao.RepairOrderDao;
import com.wt.hrentSystem.repair.entity.RepairImage;
import com.wt.hrentSystem.repair.entity.RepairLog;
import com.wt.hrentSystem.repair.entity.RepairOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 报修 v2（docs/13 §4.4）。
 * 状态机 CREATED→ACCEPTED→ON_SITE→COMPLETED→ACCEPTED_BY_TENANT，CREATED→CANCELLED；
 * 非法跳转 409，状态流转用 version 乐观锁，重复提交用 Idempotency-Key + DB 唯一键双兜底。
 */
@Service
public class RepairService {

    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private RepairOrderDao repairOrderDao;
    @Resource
    private RepairLogDao repairLogDao;
    @Resource
    private RepairImageDao repairImageDao;
    @Resource
    private V2Mapper v2Mapper;
    @Resource
    private V2Context v2Context;

    public Map<String, Object> create(Map<String, Object> body, User user, String idemKey) {
        Req.body(body);
        if (idemKey != null && idemKey.trim().length() > 64) {
            throw ApiException.bad("Idempotency-Key 超长");
        }
        // 幂等：同一 key 直接返回首次结果，不产生第二条工单
        RepairOrder first = idemKey == null ? null : repairOrderDao.findByIdemKey(idemKey);
        if (first != null) {
            return createdResult(first);
        }
        String houseId = Req.str(body, "houseId", true, 64);
        String leaseId = Req.str(body, "leaseId", false, 64);
        String type = Req.oneOf(body, "type", true,
                "WATER", "ELECTRIC", "DOOR_WINDOW", "APPLIANCE", "OTHER");
        String description = Req.str(body, "description", true, 1000);
        List<String> images = Req.strList(body, "images", 3);
        Date expectTime = Req.date(body, "expectTime", false);

        String landlordId = v2Mapper.findLandlordIdByHouseId(houseId);
        if (landlordId == null) {
            throw ApiException.notFound("房源不存在或已删除");
        }

        RepairOrder order = new RepairOrder();
        order.preInsert();
        order.setHouseId(houseId);
        order.setLeaseId(leaseId);
        order.setTenantId(user.getId());
        order.setLandlordId(landlordId);
        order.setType(type);
        order.setDescription(description);
        order.setExpectTime(expectTime);
        order.setStatus("CREATED");
        order.setVersion(0);
        order.setIdemKey(idemKey);
        try {
            repairOrderDao.insert(order);
        } catch (DuplicateKeyException e) {
            RepairOrder exists = idemKey == null ? null : repairOrderDao.findByIdemKey(idemKey);
            if (exists != null) {
                return createdResult(exists);
            }
            throw ApiException.conflict("重复提交，请稍后重试");
        }

        saveImages(order.getId(), images, "SUBMIT");
        writeLog(order.getId(), null, "CREATED", user, "提交报修");
        return createdResult(order);
    }

    public PageResult<Map<String, Object>> mine(User user, String status, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<Map<String, Object>> rows = v2Mapper.findMyRepairs(
                user.getId(), status, v2Context.isAdmin(user), offset, pageSize);
        int count = v2Mapper.countMyRepairs(user.getId(), status, v2Context.isAdmin(user));
        List<Map<String, Object>> list = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> vo = Rows.copy(row);
                vo.put("expectTime", Rows.iso(row.get("expectTime")));
                list.add(vo);
            }
        }
        return PageResult.of(pageNo, pageSize, count, list);
    }

    public Map<String, Object> get(String id, User user) {
        RepairOrder order = require(id, user);
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", order.getId());
        vo.put("houseId", order.getHouseId());
        vo.put("leaseId", order.getLeaseId());
        vo.put("tenantId", order.getTenantId());
        vo.put("landlordId", order.getLandlordId());
        vo.put("type", order.getType());
        vo.put("description", order.getDescription());
        vo.put("expectTime", V2Time.iso(order.getExpectTime()));
        vo.put("status", order.getStatus());
        vo.put("acceptedBy", order.getAcceptedBy());
        vo.put("acceptedAt", V2Time.iso(order.getAcceptedAt()));
        vo.put("arrivedAt", V2Time.iso(order.getArrivedAt()));
        vo.put("completedAt", V2Time.iso(order.getCompletedAt()));
        vo.put("confirmedAt", V2Time.iso(order.getConfirmedAt()));
        vo.put("cancelReason", order.getCancelReason());
        vo.put("tenantScore", order.getTenantScore());
        vo.put("tenantComment", order.getTenantComment());
        vo.put("version", order.getVersion());
        vo.put("createdAt", V2Time.iso(order.getCreateDate()));

        List<Map<String, Object>> logs = new ArrayList<>();
        List<RepairLog> rawLogs = repairLogDao.findByRepairId(id);
        if (rawLogs != null) {
            for (RepairLog l : rawLogs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fromStatus", l.getFromStatus());
                m.put("toStatus", l.getToStatus());
                m.put("operatorRole", l.getOperatorRole());
                m.put("remark", l.getRemark());
                m.put("createdAt", V2Time.iso(l.getCreateTime()));
                logs.add(m);
            }
        }
        vo.put("logs", logs);

        List<Map<String, Object>> imgs = new ArrayList<>();
        List<RepairImage> rawImgs = repairImageDao.findByRepairId(id, null);
        if (rawImgs != null) {
            for (RepairImage img : rawImgs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("url", img.getUrl());
                m.put("stage", img.getStage());
                m.put("sort", img.getSort());
                imgs.add(m);
            }
        }
        vo.put("images", imgs);
        return vo;
    }

    /**accept / arrive / complete / confirm / cancel 共用一条状态流转路径 */
    public Map<String, Object> transition(String id, String action, Map<String, Object> body, User user) {
        String[] edge = edgeOf(action);
        String from = edge[0];
        String to = edge[1];
        RepairOrder order = require(id, user);
        if (!from.equals(order.getStatus())) {
            throw ApiException.conflict("当前状态不允许该操作（当前 " + order.getStatus() + "，需要 " + from + "）");
        }
        assertActionRole(user, order, action);

        String remark = null;
        String acceptedBy = null;
        String acceptedAt = null;
        String arrivedAt = null;
        String completedAt = null;
        String confirmedAt = null;
        String cancelReason = null;
        Integer tenantScore = null;
        String tenantComment = null;
        String now = LocalDateTime.now(Req.ZONE).format(SQL_TIME);

        if ("accept".equals(action)) {
            acceptedBy = user.getId();
            acceptedAt = now;
        } else if ("arrive".equals(action)) {
            arrivedAt = now;
        } else if ("complete".equals(action)) {
            completedAt = now;
            remark = Req.str(body, "remark", false, 255);
            saveImages(id, Req.strList(body, "images", 3), "COMPLETE");
        } else if ("confirm".equals(action)) {
            confirmedAt = now;
            tenantScore = Req.integer(body, "score", false);
            if (tenantScore != null && (tenantScore < 1 || tenantScore > 5)) {
                throw ApiException.bad("score 必须在 1~5 之间");
            }
            tenantComment = Req.str(body, "comment", false, 500);
        } else if ("cancel".equals(action)) {
            cancelReason = Req.str(body, "reason", true, 255);
            remark = cancelReason;
        }

        int version = order.getVersion() == null ? 0 : order.getVersion();
        int affected = v2Mapper.transitionRepair(id, from, to, version,
                acceptedBy, acceptedAt, arrivedAt, completedAt, confirmedAt,
                cancelReason, tenantScore, tenantComment);
        if (affected == 0) {
            throw ApiException.conflict("工单已被其他操作更新（抢单失败），请刷新后重试");
        }
        writeLog(id, from, to, user, remark == null ? action : remark);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("repairId", id);
        result.put("fromStatus", from);
        result.put("toStatus", to);
        result.put("version", version + 1);
        return result;
    }

    private RepairOrder require(String id, User user) {
        RepairOrder order = repairOrderDao.get(id);
        if (order == null) {
            throw ApiException.notFound("报修工单不存在");
        }
        if (!v2Context.isAdmin(user)
                && !user.getId().equals(order.getTenantId())
                && !user.getId().equals(order.getLandlordId())) {
            throw ApiException.forbidden("无权操作该工单");
        }
        return order;
    }

    private void assertActionRole(User user, RepairOrder order, String action) {
        if (v2Context.isAdmin(user)) {
            return;
        }
        boolean landlordAction = "accept".equals(action) || "arrive".equals(action) || "complete".equals(action);
        String owner = landlordAction ? order.getLandlordId() : order.getTenantId();
        if (owner == null || !owner.equals(user.getId())) {
            throw ApiException.forbidden(landlordAction ? "仅房东可处理该工单" : "仅租客可执行该操作");
        }
    }

    /**待定 T-03：按更严格的「仅 CREATED 可取消」实现 */
    private String[] edgeOf(String action) {
        switch (action) {
            case "accept":
                return new String[]{"CREATED", "ACCEPTED"};
            case "arrive":
                return new String[]{"ACCEPTED", "ON_SITE"};
            case "complete":
                return new String[]{"ON_SITE", "COMPLETED"};
            case "confirm":
                return new String[]{"COMPLETED", "ACCEPTED_BY_TENANT"};
            case "cancel":
                return new String[]{"CREATED", "CANCELLED"};
            default:
                throw ApiException.bad("未知的工单动作：" + action);
        }
    }

    private void saveImages(String repairId, List<String> urls, String stage) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        List<RepairImage> list = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            RepairImage img = new RepairImage();
            img.setId(UUID.randomUUID().toString().replace("-", ""));
            img.setRepairId(repairId);
            img.setUrl(urls.get(i));
            img.setStage(stage);
            img.setSort(i);
            img.setCreateTime(new Date());
            list.add(img);
        }
        repairImageDao.batchInsert(list);
    }

    private void writeLog(String repairId, String from, String to, User user, String remark) {
        RepairLog log = new RepairLog();
        log.setId(UUID.randomUUID().toString().replace("-", ""));
        log.setRepairId(repairId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(user.getId());
        log.setOperatorRole(roleOf(user));
        log.setRemark(remark);
        log.setCreateTime(new Date());
        repairLogDao.insert(log);
    }

    private String roleOf(User user) {
        if (v2Context.isAdmin(user)) {
            return "ADMIN";
        }
        return user.getRole() != null && user.getRole() == 1 ? "LANDLORD" : "TENANT";
    }

    private Map<String, Object> createdResult(RepairOrder order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("repairId", order.getId());
        result.put("status", order.getStatus());
        result.put("createdAt", V2Time.iso(order.getCreateDate()));
        return result;
    }
}
