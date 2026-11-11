package com.wt.hrentSystem.common.config;

/**
 * 统一响应状态码常量
 * 说明：本项目源码多处引用 ResponseCodeCanstants.SUCCESS / FAILED，
 * 但原始发布包中缺失该类，导致编译失败，此为重建实现。
 */
public class ResponseCodeCanstants {

    /** 成功 */
    public static final String SUCCESS = "200";

    /** 失败 */
    public static final String FAILED = "500";

    /** 未登录 */
    public static final String LOGOUT = "600";

    private ResponseCodeCanstants() {
    }
}
