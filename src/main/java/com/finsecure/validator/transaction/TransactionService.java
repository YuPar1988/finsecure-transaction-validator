package com.finsecure.validator.transaction;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionValidator validator;
    private final TransactionRecordRepository repository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public TransactionService(TransactionValidator validator, TransactionRecordRepository repository,
                              MeterRegistry meterRegistry, Clock clock) {
        this.validator = validator;
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public ValidationResult validate(TransactionRequest request) {
        Instant now = Instant.now(clock);
        List<ValidationError> errors = validator.validate(request);
        ValidationResult.RiskLevel risk = request == null ? ValidationResult.RiskLevel.LOW : validator.assessRisk(request);

        if (errors.isEmpty()) {
            try {
                repository.saveAndFlush(new TransactionRecord(request, risk, now));
            } catch (DataIntegrityViolationException ex) {
                errors = List.of(new ValidationError("TXN_DUPLICATE", "transactionId",
                        "Transaction " + request.transactionId() + " has already been processed"));
            }
        }

        String id = request == null ? null : request.transactionId();
        ValidationResult result = errors.isEmpty()
                ? new ValidationResult(id, ValidationResult.Status.APPROVED, risk, List.of(), now)
                : new ValidationResult(id, ValidationResult.Status.REJECTED, risk, List.copyOf(errors), now);

        record(result);
        return result;
    }

    private void record(ValidationResult result) {
        Counter.builder("finsecure.transactions")
                .description("Validated transactions by outcome")
                .tag("result", result.status().name().toLowerCase())
                .tag("risk", result.riskLevel().name().toLowerCase())
                .register(meterRegistry)
                .increment();

        for (ValidationError error : result.errors()) {
            Counter.builder("finsecure.validation.errors")
                    .description("Validation rule failures by code")
                    .tag("code", error.code())
                    .register(meterRegistry)
                    .increment();
        }

        if (result.approved()) {
            log.info("Transaction {} approved (risk={})", result.transactionId(), result.riskLevel());
        } else {
            log.info("Transaction {} rejected: {}", result.transactionId(),
                    result.errors().stream().map(ValidationError::code).toList());
        }
    }
}
