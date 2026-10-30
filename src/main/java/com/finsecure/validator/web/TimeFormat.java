package com.finsecure.validator.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.finsecure.validator.config.AppProperties;

@Component("timeFormat")
public class TimeFormat {

    private final DateTimeFormatter full;
    private final DateTimeFormatter shortFmt;

    public TimeFormat(AppProperties app) {
        ZoneId zone = ZoneId.of(app.timezone());
        this.full = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z", Locale.ENGLISH).withZone(zone);
        this.shortFmt = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss", Locale.ENGLISH).withZone(zone);
    }

    public String format(Instant instant) {
        return instant == null ? "" : full.format(instant);
    }

    public String brief(Instant instant) {
        return instant == null ? "" : shortFmt.format(instant);
    }
}
