package com.wt.hrentSystem.ai.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 推荐反馈埋点 ai_feedback（V6）。
 * action: IMPRESSION / CLICK / APPLY；mode: rule / llm。
 */
@Data
public class AiFeedback implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    /** 原始输入（脱敏后） */
    private String query;
    private String houseId;
    private String action;
    /** 推荐位次 1–10 */
    private Integer position;
    private String mode;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
