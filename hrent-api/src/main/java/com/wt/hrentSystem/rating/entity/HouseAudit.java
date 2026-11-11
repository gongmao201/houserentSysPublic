package com.wt.hrentSystem.rating.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 房源核验单 house_audit（V3）。
 * 8 项各 0/1，权重和 1.00；score = Σ(命中权重) × 5，落在 0–5。
 */
@Data
public class HouseAudit implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String houseId;
    private String auditorId;
    /** 照片真实 0.20 */
    private Integer photoReal;
    /** 地址可核验 0.20 */
    private Integer addressVerified;
    /** 设施与描述一致 0.15 */
    private Integer facilityMatch;
    /** 价格合理 0.15 */
    private Integer priceReasonable;
    /** 合同模板已上传 0.10 */
    private Integer contractUploaded;
    /** 消防与安全 0.10 */
    private Integer safetyChecked;
    /** 押付规则明确 0.05 */
    private Integer depositRuleClear;
    /** 无历史投诉 0.05 */
    private Integer noComplaint;
    /** 核验分 0.00–5.00 */
    private BigDecimal score;
    private String conclusion;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
