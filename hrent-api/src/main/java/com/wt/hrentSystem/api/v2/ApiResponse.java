package com.wt.hrentSystem.api.v2;

import lombok.Data;

/**
 * v2 统一响应体 {code, message, data, traceId}。
 * HTTP 状态恒为 200，业务结果放 code（与 docs/13 §2.4 一致）。
 * 存量接口仍是字符串 code 的 ResponseResult，两者物理隔离、互不影响。
 */
@Data
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = TraceIds.get();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ApiCodes.OK, "ok", data);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(ApiCodes.OK, message, data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
