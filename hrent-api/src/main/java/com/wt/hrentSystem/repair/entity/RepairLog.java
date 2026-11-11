package com.wt.hrentSystem.repair.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 工单流转日志 repair_log（V4）。
 * 只追加，不更新、不软删。
 */
@Data
public class RepairLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String repairId;
    private String fromStatus;
    private String toStatus;
    private String operatorId;
    /** TENANT / LANDLORD / ADMIN */
    private String operatorRole;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
