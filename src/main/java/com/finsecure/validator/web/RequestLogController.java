package com.finsecure.validator.web;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.finsecure.validator.requestlog.ApiRequestLog;
import com.finsecure.validator.requestlog.LogSearchCriteria;
import com.finsecure.validator.requestlog.RequestLogService;

@Controller
public class RequestLogController {

    private static final int EXPORT_LIMIT = 10_000;

    private final RequestLogService service;

    public RequestLogController(RequestLogService service) {
        this.service = service;
    }

    @GetMapping("/logs")
    public String list(@ModelAttribute("criteria") LogSearchCriteria criteria,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "25") int size,
                       Model model) {
        Page<ApiRequestLog> result = service.search(criteria, page, size);
        model.addAttribute("page", result);
        model.addAttribute("paths", service.knownPaths());
        model.addAttribute("zone", service.zone());
        model.addAttribute("active", "logs");
        return "logs";
    }

    @GetMapping("/logs/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ApiRequestLog entry = service.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log entry not found"));
        model.addAttribute("entry", entry);
        model.addAttribute("zone", service.zone());
        model.addAttribute("active", "logs");
        return "log-detail";
    }

    @GetMapping("/logs/request/{requestId}")
    public String byRequestId(@PathVariable String requestId) {
        ApiRequestLog entry = service.findByRequestId(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log entry not found"));
        return "redirect:/logs/" + entry.getId();
    }

    @GetMapping("/logs/export")
    public ResponseEntity<byte[]> export(@ModelAttribute LogSearchCriteria criteria) {
        List<ApiRequestLog> rows = service.searchAll(criteria, EXPORT_LIMIT);
        DateTimeFormatter fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

        StringBuilder csv = new StringBuilder(
                "id,request_id,timestamp,method,path,status,duration_ms,outcome,transaction_id,client_ip,pod\n");
        for (ApiRequestLog r : rows) {
            csv.append(r.getId()).append(',')
                    .append(r.getRequestId()).append(',')
                    .append(r.getCreatedAt().atZone(service.zone()).format(fmt)).append(',')
                    .append(r.getMethod()).append(',')
                    .append(csvValue(r.getPath())).append(',')
                    .append(r.getStatusCode()).append(',')
                    .append(r.getDurationMs()).append(',')
                    .append(csvValue(r.getOutcome())).append(',')
                    .append(csvValue(r.getTransactionId())).append(',')
                    .append(csvValue(r.getClientIp())).append(',')
                    .append(csvValue(r.getPodName())).append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"api-request-log.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csvValue(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
