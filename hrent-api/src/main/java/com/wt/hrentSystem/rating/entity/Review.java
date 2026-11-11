package com.wt.hrentSystem.rating.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wt.hrentSystem.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

/**
 * 评价流水 review（V3）。
 * 表里只有 create_time/update_time/del_flag，没有 create_by/update_by/remake；
 * 因此 override preInsert/preUpdate，不走 BaseEntity 里依赖 currentUser 的逻辑。
 */
@Data
public class Review extends BaseEntity<Review> {
    private static final long serialVersionUID = 1L;

    private String leaseId;
    private String reviewerId;
    /** HOUSE / LANDLORD */
    private String targetType;
    private String targetId;
    /** 1.0–5.0 */
    private BigDecimal score;
    /** JSON 字符串：维度分 */
    private String dimensions;
    private String content;
    /** JSON 字符串：图片路径数组，最多 3 张 */
    private String images;
    /** PUBLISHED / APPEALING / HIDDEN */
    private String status;
    /** 房东回复，每条一次，<=100 字 */
    private String reply;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date replyAt;

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
