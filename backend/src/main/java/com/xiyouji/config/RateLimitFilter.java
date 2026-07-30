package com.xiyouji.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

/**
 * 应用层限流过滤器
 * - 基于 IP 的滑动窗口限流：每个 IP 每秒最多 30 次请求
 * - 分布式部署：使用 Redis Sorted Set 存储请求时间戳，保证多实例下计数正确
 *   1) ZREMRANGEBYSCORE 清理窗口外过期记录
 *   2) ZCARD 统计当前窗口请求数
 *   3) ZADD 记录本次请求
 *   4) 设置 TTL 防止冷 IP 永驻内存
 * - 单机部署 / Redis 不可用：降级为本地 ConcurrentHashMap + ConcurrentLinkedDeque
 * - 仅对 /api/game/** 路径生效，不影响静态资源、Swagger 等
 * - 超限返回 HTTP 429 和 JSON 错误体 {"error":"请求过于频繁","code":429}
 * - 优先级仅次于 RequestTraceFilter，确保限流日志也带 traceId
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** 每秒最大请求数（提高至30以兼容前端连续请求场景） */
    private static final int MAX_REQUESTS_PER_SECOND = 30;

    /** 滑动窗口长度（毫秒） */
    private static final long WINDOW_MS = 1000L;

    /** 限流生效的路径前缀 */
    private static final String GAME_API_PREFIX = "/api/game/";

    /** Redis Key 前缀 */
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:ip:";

    /** 触发限流时返回的 JSON 错误体 */
    private static final String RATE_LIMIT_BODY = "{\"error\":\"请求过于频繁\",\"code\":429}";

    /** Redis Sorted Set 操作，当 app.redis.session-enabled=false 或注入失败时为 null（降级本地） */
    private final ZSetOperations<String, Object> zSetOps;

    /** 是否启用 Redis 限流（依赖 app.redis.session-enabled=true 且 RedisTemplate 可用） */
    private final boolean redisEnabled;

    /** 降级方案：本地每个 IP 最近 1 秒内的请求时间戳队列 */
    private final ConcurrentHashMap<String, Deque<Long>> localTimestamps = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.redis.session-enabled:false}") boolean redisSessionEnabled,
            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider) {
        // 使用 ObjectProvider 可选注入：当 app.redis.session-enabled=false 时 RedisConfig 不创建 Bean，
        // 此处 redisTemplateProvider.getIfAvailable() 返回 null，自动降级为本地限流，不影响应用启动
        RedisTemplate<String, Object> redisTemplate = redisSessionEnabled ? redisTemplateProvider.getIfAvailable() : null;
        this.zSetOps = (redisTemplate != null) ? redisTemplate.opsForZSet() : null;
        this.redisEnabled = this.zSetOps != null;
        log.info("RateLimitFilter initialized: redisEnabled={}, mode={}",
                redisEnabled, redisEnabled ? "REDIS_SORTED_SET" : "LOCAL_FALLBACK");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();

        // 只对 /api/game/** 路径限流，其它请求直接放行
        if (requestUri == null || !requestUri.startsWith(GAME_API_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        boolean allowed = redisEnabled
                ? allowRequestRedis(clientIp)
                : allowRequestLocal(clientIp);

        if (!allowed) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(RATE_LIMIT_BODY);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Redis 滑动窗口限流（多实例共享计数）
     * 算法：
     *   1) 清理窗口外过期记录
     *   2) 检查窗口内请求数是否达上限
     *   3) 未达上限则 ZADD 记录本次请求
     * 注意：非原子操作，理论上存在极小概率多请求同时通过检查后都 ZADD，但 30/s 阈值留有余量可接受。
     *       若需严格原子可改用 Lua 脚本（后续优化）。
     */
    private boolean allowRequestRedis(String clientIp) {
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;
        String member = now + "-" + UUID.randomUUID();  // 唯一 member，避免同毫秒覆盖

        try {
            // 1. 清理窗口外的过期时间戳
            zSetOps.removeRangeByScore(key, 0, windowStart);

            // 2. 检查窗口内请求数
            Long count = zSetOps.zCard(key);
            if (count != null && count >= MAX_REQUESTS_PER_SECOND) {
                return false;
            }

            // 3. 记录本次请求
            zSetOps.add(key, member, now);

            // 4. 设置/刷新 TTL，防止冷 IP 数据永驻内存
            zSetOps.getOperations().expire(key, 2, TimeUnit.SECONDS);

            return true;
        } catch (Exception e) {
            // Redis 异常时降级到本地限流，保证服务可用
            log.warn("Redis rate limit failed, fallback to local: {}", e.getMessage());
            return allowRequestLocal(clientIp);
        }
    }

    /**
     * 本地滑动窗口限流（单机降级方案）
     */
    private boolean allowRequestLocal(String clientIp) {
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;
        Deque<Long> timestamps = localTimestamps.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_SECOND) {
                return false;
            }
            timestamps.addLast(now);
        }
        return true;
    }

    /**
     * 解析客户端真实IP
     * 优先取反向代理转发的 X-Forwarded-For / X-Real-IP，取不到再用 remoteAddr
     */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int commaIndex = ip.indexOf(',');
            return commaIndex > 0 ? ip.substring(0, commaIndex).trim() : ip.trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        return request.getRemoteAddr();
    }
}
