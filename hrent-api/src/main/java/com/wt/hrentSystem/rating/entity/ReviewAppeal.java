package com.wt.hrentSystem.rating.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 评价申诉 review_appeal（V3）。
 * 唯一键 (review_id, applicant_id)；申诉期间评价不计入均分。
 */
@Data
public class ReviewAppeal implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String reviewId;
    private String applicantId;
    private String reason;
    /** PENDING / ACCEPTED / REJECTED */
    private String status;
    private String handledBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date handledAt;
    private String result;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
