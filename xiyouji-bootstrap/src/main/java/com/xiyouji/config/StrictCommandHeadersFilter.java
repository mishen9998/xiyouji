package com.xiyouji.config;

import com.xiyouji.exception.ExpectedStateVersionRequiredException;
import com.xiyouji.exception.IdempotencyKeyRequiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces the phase-two command contract before a controller can mutate
 * state. The actual fingerprint/replay logic remains in the application
 * services so it is also covered by unit tests.
 */
@Component
public class StrictCommandHeadersFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY = "X-Idempotency-Key";
    private static final String VERSION = "X-Expected-State-Version";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isBusinessCommand(request)) {
            String key = request.getHeader(IDEMPOTENCY);
            if (key == null || key.isBlank() || key.length() > 128) {
                writeError(response, new IdempotencyKeyRequiredException());
                return;
            }
            if (requiresVersion(request)) {
                String version = request.getHeader(VERSION);
                try {
                    if (version == null || version.isBlank() || Long.parseLong(version) < 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ex) {
                    writeError(response, new ExpectedStateVersionRequiredException());
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isBusinessCommand(HttpServletRequest request) {
        String method = request.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method))) {
            return false;
        }
        String path = request.getRequestURI();
        if (!path.startsWith(request.getContextPath() + "/api/")) return false;
        return !path.equals(request.getContextPath() + "/api/auth/login");
    }

    private boolean requiresVersion(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        String relative = path.substring(context.length());
        return !(relative.equals("/api/auth/register")
                || relative.equals("/api/auth/guest")
                || relative.equals("/api/game/new")
                || relative.equals("/api/room/create"));
    }

    private void writeError(HttpServletResponse response, com.xiyouji.exception.BusinessException error)
            throws IOException {
        response.setStatus(error.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + error.getErrorCode()
                + "\",\"message\":\"" + error.getMessage() + "\",\"status\":"
                + error.getHttpStatus() + "}");
    }
}
