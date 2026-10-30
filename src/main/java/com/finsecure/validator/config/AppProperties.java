package com.finsecure.validator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("FinSecure Pay Transaction Validator") String name,
        @DefaultValue("dev") String version,
        @DefaultValue("local") String environment,
        @DefaultValue("") String imageTag,
        @DefaultValue("local") String podName,
        @DefaultValue("") String prometheusUrl,
        @DefaultValue("") String grafanaUrl,
        @DefaultValue("4000") int maxLoggedBodyLength,
        @DefaultValue("Asia/Singapore") String timezone,
        Admin admin) {

    public record Admin(@DefaultValue("admin") String username, @DefaultValue("") String password) {
    }

    public AppProperties {
        if (admin == null) {
            admin = new Admin("admin", "");
        }
    }
}
