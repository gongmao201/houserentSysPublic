package com.wt.hrentSystem.repair.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wt.hrentSystem.common.entity.BaseEntity;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * 报修工单 repair_order（V4）。
 * 表里有 del_flag，但没有 create_by/update_by/remake；
 * override preInsert/preUpdate，避免 BaseEntity 依赖 currentUser 导致 NPE。
 */
@Data
public class RepairOrder extends BaseEntity<RepairOrder> {
    private static final long serialVersionUID = 1L;

    private String houseId;
    private String leaseId;
    private String tenantId;
    /** 冗余房东 id，便于按房东查 */
    private String landlordId;
    /** WATER / ELECTRIC / DOOR_WINDOW / APPLIANCE / OTHER */
    private String type;
    private String description;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expectTime;
    /** CREATED / ACCEPTED / ON_SITE / COMPLETED / ACCEPTED_BY_TENANT / CANCELLED */
    private String status;
    private String acceptedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date acceptedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date arrivedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date completedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date confirmedAt;
    private String cancelReason;
    /** 租客对维修评分 1–5 */
    private Integer tenantScore;
    private String tenantComment;
    /** 乐观锁 */
    private Integer version;
    /** 幂等键（Idempotency-Key） */
    private String idemKey;

    @Override
    public void preInsert() {
        if (this.getId() == null || "".equals(this.getId())) {
            this.setId(UUID.randomUUID().toString().replaceAll("-", ""));
        }
        Date now = new Date();
        this.setCreateDate(now);
        this.setUpdateDate(now);
        this.setDelFlag(0);
    }

    @Override
    public void preUpdate() {
        this.setUpdateDate(new Date());
    }
}
