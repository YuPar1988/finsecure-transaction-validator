package com.finsecure.validator.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.finsecure.validator.config.ValidationProperties;

/**
 * Controlled failure used to show that a failing test stops the pipeline before deployment.
 * The expectation below is deliberately wrong: 15,000 is above the 10,000 limit.
 */
class ControlledFailureTest {

    @Test
    void amountAboveLimitIsWronglyExpectedToPass() {
        TransactionRecordRepository repository = Mockito.mock(TransactionRecordRepository.class);
        TransactionValidator validator = new TransactionValidator(new ValidationProperties(
                new BigDecimal("0.01"), new BigDecimal("10000.00"), new BigDecimal("5000.00"),
                List.of("SGD"), List.of()), repository);

        TransactionRequest request = new TransactionRequest("TXN-FAIL-001", "4111111111111111", "MER1001",
                new BigDecimal("15000.00"), "SGD", "SG");

        assertThat(validator.validate(request)).isEmpty();
    }
}
