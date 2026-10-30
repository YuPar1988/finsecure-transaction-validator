package com.finsecure.validator.transaction;

public record ValidationError(String code, String field, String message) {
}
