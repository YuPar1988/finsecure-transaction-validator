package com.finsecure.validator.web;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.finsecure.validator.monitoring.MonitoringService;
import com.finsecure.validator.requestlog.DashboardStats;
import com.finsecure.validator.requestlog.RequestLogService;

@Controller
public class PageController {

    private final RequestLogService logService;
    private final MonitoringService monitoringService;

    public PageController(RequestLogService logService, MonitoringService monitoringService) {
        this.logService = logService;
        this.monitoringService = monitoringService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "24h") String window, Model model) {
        RequestLogService.Window w = RequestLogService.Window.from(window);
        model.addAttribute("stats", logService.stats(w));
        model.addAttribute("window", w.code());
        model.addAttribute("zone", logService.zone());
        model.addAttribute("active", "dashboard");
        return "dashboard";
    }

    @GetMapping(path = "/dashboard/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public DashboardStats dashboardData(@RequestParam(defaultValue = "24h") String window) {
        return logService.stats(RequestLogService.Window.from(window));
    }

    @GetMapping("/monitoring")
    public String monitoring(Model model) {
        model.addAttribute("snapshot", monitoringService.snapshot());
        model.addAttribute("active", "monitoring");
        return "monitoring";
    }

    @GetMapping(path = "/monitoring/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> monitoringData() {
        return monitoringService.snapshot();
    }

    @GetMapping("/tester")
    public String tester(Model model) {
        model.addAttribute("active", "tester");
        return "tester";
    }
}
