package com.apollo.elevator.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Slf4j
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_ATTRIBUTE = RequestLoggingInterceptor.class.getName() + ".requestId";
    private static final String START_TIME_ATTRIBUTE = RequestLoggingInterceptor.class.getName() + ".startTime";
    private static final String ROUTE_ATTRIBUTE = RequestLoggingInterceptor.class.getName() + ".route";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = UUID.randomUUID().toString();
        String route = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        route = route == null ? "<unmatched>" : route;
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        request.setAttribute(ROUTE_ATTRIBUTE, route);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_REQUEST_ID, requestId);

        log.info("HTTP request started. requestId={}, method={}, route={}", requestId, request.getMethod(), route);
        log.debug(
                "HTTP request metadata. requestId={}, contentType={}, contentLength={}",
                requestId,
                request.getContentType(),
                request.getContentLengthLong()
        );
        if (handler instanceof HandlerMethod handlerMethod) {
            log.debug(
                    "HTTP handler resolved. requestId={}, handler={}.{}",
                    requestId,
                    handlerMethod.getBeanType().getSimpleName(),
                    handlerMethod.getMethod().getName()
            );
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        String requestId = (String) request.getAttribute(REQUEST_ID_ATTRIBUTE);
        String route = (String) request.getAttribute(ROUTE_ATTRIBUTE);
        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);
        long durationMs = startTime instanceof Long startedAt
                ? (System.nanoTime() - startedAt) / 1_000_000
                : -1;

        if (response.getStatus() >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            log.error(
                    "HTTP request failed. requestId={}, method={}, route={}, status={}, durationMs={}, exceptionType={}",
                    requestId,
                    request.getMethod(),
                    route,
                    response.getStatus(),
                    durationMs,
                    exception == null ? "handled-or-unavailable" : exception.getClass().getSimpleName()
            );
        } else {
            log.info(
                    "HTTP request completed. requestId={}, method={}, route={}, status={}, durationMs={}",
                    requestId,
                    request.getMethod(),
                    route,
                    response.getStatus(),
                    durationMs
            );
        }
        MDC.remove(MDC_REQUEST_ID);
    }
}
