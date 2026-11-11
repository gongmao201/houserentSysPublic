package com.wt.hrentSystem.repair.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 工单图片 repair_image（V4）。
 * stage: SUBMIT / COMPLETE；只追加，不软删。
 */
@Data
public class RepairImage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String repairId;
    /** 相对路径 /uploads/... */
    private String url;
    private String stage;
    private Integer sort;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
