package com.wt.hrentSystem.api.v2;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * 请求级 traceId：一次 v2 请求内所有响应与日志共用同一个值。
 * 由 V2TraceFilter 在入口生成；拿不到请求上下文时退化为新生成的 id（不抛异常、不静默）。
 */
public final class TraceIds {

    public static final String ATTR = "V2_TRACE_ID";

    private TraceIds() {
    }

    public static String get() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
            Object cached = request.getAttribute(ATTR);
            if (cached != null) {
                return String.valueOf(cached);
            }
            String id = newId();
            request.setAttribute(ATTR, id);
            return id;
        }
        return newId();
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
