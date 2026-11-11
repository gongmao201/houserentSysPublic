package com.wt.hrentSystem.api.v2;

/**
 * v2 业务异常：携带业务码，由 ApiExceptionHandler 转成响应体。
 */
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ApiException bad(String message) {
        return new ApiException(ApiCodes.BAD_REQUEST, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ApiCodes.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ApiCodes.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ApiCodes.CONFLICT, message);
    }

    public static ApiException unprocessable(String message) {
        return new ApiException(ApiCodes.UNPROCESSABLE, message);
    }
}
