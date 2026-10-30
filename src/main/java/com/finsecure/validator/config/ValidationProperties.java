package com.finsecure.validator.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "finsecure.validation")
public record ValidationProperties(
        @DefaultValue("0.01") BigDecimal minAmount,
        @DefaultValue("10000.00") BigDecimal maxAmount,
        @DefaultValue("5000.00") BigDecimal highRiskAmount,
        @DefaultValue({"SGD", "USD", "EUR", "GBP", "MYR"}) List<String> allowedCurrencies,
        @DefaultValue({"KP", "IR", "SY"}) List<String> blockedCountries) {
}
