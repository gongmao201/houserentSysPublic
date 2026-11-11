package com.wt.hrentSystem.common.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户多角色表 user_role（V2）。
 * 与存量 sys_user.role 并存：老角色只读，新逻辑读这张表。
 */
@Data
public class UserRole implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    /** TENANT / LANDLORD / ADMIN */
    private String role;
    /** 作用域预留：某小区/某门店 */
    private String scopeId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
