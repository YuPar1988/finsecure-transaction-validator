package com.finsecure.validator.transaction;

import java.math.BigDecimal;

public record TransactionRequest(
        String transactionId,
        String accountNumber,
        String merchantId,
        BigDecimal amount,
        String currency,
        String country) {
}
