package com.finsecure.validator.requestlog;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsecure.validator.config.AppProperties;

import jakarta.persistence.criteria.Predicate;

@Service
@Transactional(readOnly = true)
public class RequestLogService {

    public enum Window {
        HOUR("1h", Duration.ofHours(1), ChronoUnit.MINUTES, 5, "HH:mm"),
        DAY("24h", Duration.ofHours(24), ChronoUnit.HOURS, 1, "HH:00"),
        WEEK("7d", Duration.ofDays(7), ChronoUnit.DAYS, 1, "dd MMM");

        private final String code;
        private final Duration length;
        private final ChronoUnit bucketUnit;
        private final int bucketSize;
        private final String labelPattern;

        Window(String code, Duration length, ChronoUnit bucketUnit, int bucketSize, String labelPattern) {
            this.code = code;
            this.length = length;
            this.bucketUnit = bucketUnit;
            this.bucketSize = bucketSize;
            this.labelPattern = labelPattern;
        }

        public String code() {
            return code;
        }

        public static Window from(String code) {
            for (Window w : values()) {
                if (w.code.equalsIgnoreCase(code)) {
                    return w;
                }
            }
            return DAY;
        }
    }

    private final ApiRequestLogRepository repository;
    private final Clock clock;
    private final ZoneId zone;

    public RequestLogService(ApiRequestLogRepository repository, Clock clock, AppProperties app) {
        this.repository = repository;
        this.clock = clock;
        this.zone = ZoneId.of(app.timezone());
    }

    public ZoneId zone() {
        return zone;
    }

    public Page<ApiRequestLog> search(LogSearchCriteria criteria, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 5, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(toSpecification(criteria), pageable);
    }

    public List<ApiRequestLog> searchAll(LogSearchCriteria criteria, int limit) {
        return repository.findAll(toSpecification(criteria),
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    public Optional<ApiRequestLog> find(Long id) {
        return repository.findById(id);
    }

    public Optional<ApiRequestLog> findByRequestId(String requestId) {
        return repository.findByRequestId(requestId);
    }

    public List<String> knownPaths() {
        return repository.distinctPaths();
    }

    public DashboardStats stats(Window window) {
        Instant now = Instant.now(clock);
        Instant since = now.minus(window.length);

        long total = repository.countByCreatedAtGreaterThanEqual(since);
        long serverErrors = repository.countByCreatedAtGreaterThanEqualAndStatusCodeGreaterThanEqual(since, 500);
        long allErrors = repository.countByCreatedAtGreaterThanEqualAndStatusCodeGreaterThanEqual(since, 400);
        long approved = repository.countByCreatedAtGreaterThanEqualAndOutcome(since, "APPROVED");
        long rejected = repository.countByCreatedAtGreaterThanEqualAndOutcome(since, "REJECTED");
        Double avg = repository.averageDurationSince(since);
        Long max = repository.maxDurationSince(since);

        Map<Integer, Long> statuses = new LinkedHashMap<>();
        for (Object[] row : repository.countByStatusSince(since)) {
            statuses.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        List<DashboardStats.EndpointSummary> endpoints = new ArrayList<>();
        for (Object[] row : repository.endpointSummarySince(since)) {
            endpoints.add(new DashboardStats.EndpointSummary((String) row[0], (String) row[1],
                    ((Number) row[2]).longValue(), row[3] == null ? 0 : Math.round(((Number) row[3]).doubleValue())));
        }

        List<String> labels = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        buildTimeline(window, now, repository.timestampsSince(since), labels, counts);

        double successRate = total == 0 ? 100.0 : round((total - allErrors) * 100.0 / total);
        long decided = approved + rejected;
        double approvalRate = decided == 0 ? 0.0 : round(approved * 100.0 / decided);

        return new DashboardStats(window.code(), total, repository.count(), serverErrors, allErrors - serverErrors,
                approved, rejected, successRate, approvalRate,
                avg == null ? 0 : Math.round(avg), max == null ? 0 : max,
                labels, counts, statuses, endpoints, repository.findTop10ByOrderByCreatedAtDesc());
    }

    private void buildTimeline(Window window, Instant now, List<Instant> timestamps,
                               List<String> labels, List<Long> counts) {
        ZonedDateTime end = now.atZone(zone).truncatedTo(window.bucketUnit);
        if (window.bucketUnit == ChronoUnit.MINUTES) {
            end = end.withMinute(end.getMinute() - end.getMinute() % window.bucketSize);
        }
        Duration bucket = Duration.of(window.bucketSize, window.bucketUnit);
        int buckets = (int) (window.length.toMinutes() / bucket.toMinutes());
        ZonedDateTime start = end.minus(bucket.multipliedBy(buckets - 1L));

        long[] values = new long[buckets];
        long bucketMillis = bucket.toMillis();
        long startMillis = start.toInstant().toEpochMilli();
        for (Instant ts : timestamps) {
            long index = (ts.toEpochMilli() - startMillis) / bucketMillis;
            if (index >= 0 && index < buckets) {
                values[(int) index]++;
            }
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(window.labelPattern, Locale.ENGLISH);
        for (int i = 0; i < buckets; i++) {
            labels.add(start.plus(bucket.multipliedBy(i)).format(fmt));
            counts.add(values[i]);
        }
    }

    private Specification<ApiRequestLog> toSpecification(LogSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(c.getQ())) {
                String like = "%" + c.getQ().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("requestId")), like),
                        cb.like(cb.lower(root.get("transactionId")), like),
                        cb.like(cb.lower(root.get("clientIp")), like)));
            }
            if (hasText(c.getPath())) {
                predicates.add(cb.equal(root.get("path"), c.getPath()));
            }
            if (hasText(c.getMethod())) {
                predicates.add(cb.equal(root.get("method"), c.getMethod().toUpperCase(Locale.ROOT)));
            }
            if (hasText(c.getOutcome())) {
                predicates.add(cb.equal(root.get("outcome"), c.getOutcome().toUpperCase(Locale.ROOT)));
            }
            if (hasText(c.getStatusGroup())) {
                switch (c.getStatusGroup()) {
                    case "2xx" -> predicates.add(cb.between(root.get("statusCode"), 200, 299));
                    case "4xx" -> predicates.add(cb.between(root.get("statusCode"), 400, 499));
                    case "5xx" -> predicates.add(cb.between(root.get("statusCode"), 500, 599));
                    default -> { }
                }
            }
            if (c.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        c.getFrom().atStartOfDay(zone).toInstant()));
            }
            if (c.getTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        c.getTo().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
