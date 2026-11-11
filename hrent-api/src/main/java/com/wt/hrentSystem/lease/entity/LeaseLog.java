package com.wt.hrentSystem.lease.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 租约流转日志 lease_log（V2）。
 * 只追加，不更新、不软删。
 */
@Data
public class LeaseLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String leaseId;
    /** 原状态 0/1/2/3 */
    private Integer fromStatus;
    /** 新状态 0/1/2/3 */
    private Integer toStatus;
    private String operatorId;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
