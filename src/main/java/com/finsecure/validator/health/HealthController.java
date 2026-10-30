package com.finsecure.validator.health;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finsecure.validator.config.AppProperties;

@RestController
public class HealthController {

    private final HealthEndpoint healthEndpoint;
    private final AppProperties app;
    private final Clock clock;

    public HealthController(HealthEndpoint healthEndpoint, AppProperties app, Clock clock) {
        this.healthEndpoint = healthEndpoint;
        this.app = app;
        this.clock = clock;
    }

    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        Status status = healthEndpoint.health().getStatus();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.getCode());
        body.put("service", app.name());
        body.put("version", app.version());
        body.put("environment", app.environment());
        body.put("pod", app.podName());
        body.put("timestamp", Instant.now(clock).toString());

        HttpStatus httpStatus = Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(body);
    }
}
