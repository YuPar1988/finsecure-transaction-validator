package com.finsecure.validator.monitoring;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.IndicatedHealthDescriptor;
import org.springframework.stereotype.Service;

import com.finsecure.validator.config.AppProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class MonitoringService {

    private final MeterRegistry registry;
    private final HealthEndpoint healthEndpoint;
    private final AppProperties app;

    public MonitoringService(MeterRegistry registry, HealthEndpoint healthEndpoint, AppProperties app) {
        this.registry = registry;
        this.healthEndpoint = healthEndpoint;
        this.app = app;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> data = new LinkedHashMap<>();

        HealthDescriptor health = healthEndpoint.health();
        data.put("status", health.getStatus().getCode());
        data.put("components", components(health));

        data.put("version", app.version());
        data.put("imageTag", app.imageTag());
        data.put("environment", app.environment());
        data.put("pod", app.podName());

        double uptimeSeconds = gauge("process.uptime");
        data.put("uptimeSeconds", Math.round(uptimeSeconds));
        data.put("uptime", formatDuration(Duration.ofSeconds((long) uptimeSeconds)));

        double heapUsed = sumGauges("jvm.memory.used", "area", "heap");
        double heapMax = sumGauges("jvm.memory.max", "area", "heap");
        data.put("heapUsedMb", Math.round(heapUsed / 1024 / 1024));
        data.put("heapMaxMb", Math.round(heapMax / 1024 / 1024));
        data.put("heapPercent", heapMax > 0 ? Math.round(heapUsed * 100 / heapMax) : 0);

        data.put("processCpuPercent", Math.round(gauge("process.cpu.usage") * 1000) / 10.0);
        data.put("systemCpuPercent", Math.round(gauge("system.cpu.usage") * 1000) / 10.0);
        data.put("liveThreads", Math.round(gauge("jvm.threads.live")));
        data.put("dbActiveConnections", Math.round(gauge("hikaricp.connections.active")));
        data.put("dbIdleConnections", Math.round(gauge("hikaricp.connections.idle")));

        data.put("approvedTotal", Math.round(counter("finsecure.transactions", "result", "approved")));
        data.put("rejectedTotal", Math.round(counter("finsecure.transactions", "result", "rejected")));
        data.put("loginFailures", Math.round(counter("finsecure.logins", "result", "failure")));

        data.put("endpoints", httpEndpoints());
        data.put("prometheusUrl", app.prometheusUrl());
        data.put("grafanaUrl", app.grafanaUrl());
        return data;
    }

    private List<Map<String, Object>> components(HealthDescriptor health) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (health instanceof CompositeHealthDescriptor composite && composite.getComponents() != null) {
            composite.getComponents().forEach((name, component) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", name);
                item.put("status", component.getStatus().getCode());
                if (component instanceof IndicatedHealthDescriptor indicated && indicated.getDetails() != null
                        && !indicated.getDetails().isEmpty()) {
                    item.put("details", indicated.getDetails().toString());
                }
                list.add(item);
            });
        }
        return list;
    }

    private List<Map<String, Object>> httpEndpoints() {
        Map<String, double[]> grouped = new LinkedHashMap<>();
        for (Timer timer : registry.find("http.server.requests").timers()) {
            String uri = timer.getId().getTag("uri");
            if (uri == null || !(uri.startsWith("/validate") || uri.startsWith("/health"))) {
                continue;
            }
            String key = timer.getId().getTag("method") + " " + uri + " " + timer.getId().getTag("status");
            double[] values = grouped.computeIfAbsent(key, k -> new double[3]);
            values[0] += timer.count();
            values[1] += timer.totalTime(TimeUnit.MILLISECONDS);
            values[2] = Math.max(values[2], timer.max(TimeUnit.MILLISECONDS));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((key, v) -> {
            String[] parts = key.split(" ");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("method", parts[0]);
            row.put("uri", parts[1]);
            row.put("status", parts[2]);
            row.put("count", Math.round(v[0]));
            row.put("avgMs", v[0] == 0 ? 0 : Math.round(v[1] / v[0] * 10) / 10.0);
            row.put("maxMs", Math.round(v[2] * 10) / 10.0);
            result.add(row);
        });
        return result;
    }

    private double gauge(String name) {
        Gauge gauge = registry.find(name).gauge();
        if (gauge == null) {
            return 0;
        }
        double value = gauge.value();
        return Double.isNaN(value) ? 0 : value;
    }

    private double sumGauges(String name, String tag, String tagValue) {
        return registry.find(name).tag(tag, tagValue).gauges().stream()
                .mapToDouble(Gauge::value)
                .filter(v -> !Double.isNaN(v) && v > 0)
                .sum();
    }

    private double counter(String name, String tag, String tagValue) {
        return registry.find(name).tag(tag, tagValue).counters().stream().mapToDouble(Counter::count).sum();
    }

    private static String formatDuration(Duration d) {
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + d.toSecondsPart() + "s";
    }
}
