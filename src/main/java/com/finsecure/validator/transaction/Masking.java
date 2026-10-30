package com.finsecure.validator.transaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Masking {

    private static final Pattern ACCOUNT_IN_JSON =
            Pattern.compile("(\"accountNumber\"\\s*:\\s*\")([^\"]*)(\")");

    private Masking() {
    }

    public static String maskAccount(String account) {
        if (account == null || account.isBlank()) {
            return account;
        }
        String trimmed = account.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }

    public static String maskJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        Matcher matcher = ACCOUNT_IN_JSON.matcher(json);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String masked = maskAccount(matcher.group(2));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + masked + matcher.group(3)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
