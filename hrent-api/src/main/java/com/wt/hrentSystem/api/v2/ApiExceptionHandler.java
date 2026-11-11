package com.wt.hrentSystem.api.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * v2 全局异常处理。
 * 用 basePackages 限定作用域，避免影响存量 14 个 Controller 的异常行为。
 * 未捕获异常：日志留堆栈，前端只拿到 code=500 + traceId，绝不返回堆栈/SQL。
 */
@RestControllerAdvice(basePackages = "com.wt.hrentSystem.api.v2")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ApiResponse<Object> onBusiness(ApiException e) {
        log.warn("v2 业务异常 traceId=" + TraceIds.get() + " code=" + e.getCode()
                + " message=" + e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Object> onBadJson(HttpMessageNotReadableException e) {
        log.warn("v2 请求体解析失败 traceId=" + TraceIds.get(), e);
        return ApiResponse.fail(ApiCodes.BAD_REQUEST, "请求体不是合法 JSON");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Object> onMissingParam(MissingServletRequestParameterException e) {
        log.warn("v2 缺少参数 traceId=" + TraceIds.get() + " name=" + e.getParameterName());
        return ApiResponse.fail(ApiCodes.BAD_REQUEST, "缺少参数：" + e.getParameterName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Object> onIllegalArgument(IllegalArgumentException e) {
        log.warn("v2 参数非法 traceId=" + TraceIds.get(), e);
        return ApiResponse.fail(ApiCodes.BAD_REQUEST, "参数不合法：" + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> onUnexpected(Exception e) {
        String traceId = TraceIds.get();
        log.error("v2 未捕获异常 traceId=" + traceId, e);
        return ApiResponse.fail(ApiCodes.SERVER_ERROR, "服务异常，请复制 traceId（" + traceId + "）联系管理员");
    }
}
