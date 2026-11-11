package com.wt.hrentSystem.api.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * v2 链路打点：traceId + URI + 耗时。
 * 只处理 /api/v2/**，存量请求直接放行，不改变任何存量行为。
 * 异常不在此捕获（交给 ApiExceptionHandler），避免静默吞错。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class V2TraceFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(V2TraceFilter.class);

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (!(req instanceof HttpServletRequest)) {
            chain.doFilter(req, res);
            return;
        }
        HttpServletRequest request = (HttpServletRequest) req;
        String uri = request.getRequestURI();
        if (uri == null || !uri.contains("/api/v2/")) {
            chain.doFilter(req, res);
            return;
        }
        String traceId = TraceIds.newId();
        request.setAttribute(TraceIds.ATTR, traceId);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            log.info("v2 traceId=" + traceId + " method=" + request.getMethod()
                    + " uri=" + uri + " cost=" + (System.currentTimeMillis() - start) + "ms");
        }
    }
}
