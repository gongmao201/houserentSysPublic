package com.wt.hrentSystem.bill.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 账单明细 bill_item（V5）。
 * 金额单位：分；服务层必须校验 SUM(amount_fen) = hrent_rent.amount_fen。
 */
@Data
public class BillItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String rentId;
    /** RENT / WATER / ELECTRIC / PROPERTY / OTHER */
    private String itemType;
    private Long amountFen;
    private String remark;
    private Integer sort;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
