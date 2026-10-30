package com.finsecure.validator.requestlog;

import java.util.List;
import java.util.Map;

public record DashboardStats(
        String window,
        long totalRequests,
        long allTimeRequests,
        long serverErrors,
        long clientErrors,
        long approved,
        long rejected,
        double successRate,
        double approvalRate,
        long averageMs,
        long maxMs,
        List<String> timelineLabels,
        List<Long> timelineCounts,
        Map<Integer, Long> statusBreakdown,
        List<EndpointSummary> endpoints,
        List<ApiRequestLog> recent) {

    public record EndpointSummary(String method, String path, long count, long averageMs) {
    }
}
