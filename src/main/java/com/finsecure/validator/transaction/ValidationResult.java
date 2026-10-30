package com.finsecure.validator.transaction;

import java.time.Instant;
import java.util.List;

public record ValidationResult(
        String transactionId,
        Status status,
        RiskLevel riskLevel,
        List<ValidationError> errors,
        Instant validatedAt) {

    public enum Status { APPROVED, REJECTED }

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public boolean approved() {
        return status == Status.APPROVED;
    }
}
