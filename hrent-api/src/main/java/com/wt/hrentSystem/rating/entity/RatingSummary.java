package com.wt.hrentSystem.rating.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 评分汇总 rating_summary（V3）。
 * 复合主键 (target_type, target_id)；没有 id，不能继承 BaseEntity。
 */
@Data
public class RatingSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    /** HOUSE / LANDLORD */
    private String targetType;
    private String targetId;
    /** 核验分，无核验为 NULL */
    private BigDecimal officialScore;
    /** 用户原始均分 */
    private BigDecimal userScore;
    /** 贝叶斯平滑后展示分 */
    private BigDecimal displayScore;
    private Integer reviewCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
