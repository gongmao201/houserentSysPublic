package com.wt.hrentSystem.api.v2;

/**
 * v2 业务码，与 docs/13 §2.6 一一对应。
 * 禁止各模块自定义私有码（如 4001/5001）。
 */
public final class ApiCodes {

    public static final int OK = 200;
    public static final int BAD_REQUEST = 400;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int UNPROCESSABLE = 422;
    public static final int TOO_MANY = 429;
    public static final int SERVER_ERROR = 500;
    /** 沿用存量：未登录 / token 过期，不改成 401 */
    public static final int UNAUTHORIZED = 600;

    private ApiCodes() {
    }
}
