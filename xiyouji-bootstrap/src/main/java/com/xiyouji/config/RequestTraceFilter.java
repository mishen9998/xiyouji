package com.xiyouji.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪ID过滤器
 * - 为每个请求生成一个唯一的追踪ID（取UUID前8位）
 * - 将 traceId 放入 SLF4J MDC（key="traceId"），便于日志关联
 * - 同时添加到响应头 X-Trace-Id，便于前端/链路追踪
 * - 请求结束后清理 MDC，避免线程池复用导致的脏数据
 * - 最高优先级执行，确保后续所有过滤器与业务日志都能拿到 traceId
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    /** MDC 中存放 traceId 的 key */
    public static final String MDC_TRACE_ID = "traceId";

    /** 响应头名称 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 生成UUID并取前8位作为追踪ID（足够区分且日志更简洁）
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 放入 MDC，供日志模式 %X{traceId} 输出
        MDC.put(MDC_TRACE_ID, traceId);

        // 写入响应头，便于客户端/网关关联同一次请求
        response.setHeader(HEADER_TRACE_ID, traceId);

        try {
            // 继续过滤器链
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后清理 MDC，防止线程复用导致 traceId 串号
            MDC.remove(MDC_TRACE_ID);
        }
    }
}
