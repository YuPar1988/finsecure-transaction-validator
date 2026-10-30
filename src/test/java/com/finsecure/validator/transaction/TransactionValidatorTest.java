package com.finsecure.validator.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.finsecure.validator.config.ValidationProperties;

class TransactionValidatorTest {

    private TransactionRecordRepository repository;
    private TransactionValidator validator;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRecordRepository.class);
        when(repository.existsByTransactionId(anyString())).thenReturn(false);
        ValidationProperties properties = new ValidationProperties(
                new BigDecimal("0.01"), new BigDecimal("10000.00"), new BigDecimal("5000.00"),
                List.of("SGD", "USD", "EUR"), List.of("KP", "IR"));
        validator = new TransactionValidator(properties, repository);
    }

    private static TransactionRequest valid() {
        return new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                new BigDecimal("150.75"), "SGD", "SG");
    }

    private static List<String> codes(List<ValidationError> errors) {
        return errors.stream().map(ValidationError::code).toList();
    }

    @Test
    @DisplayName("a well-formed transaction passes every rule")
    void validTransactionHasNoErrors() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void nullRequestIsRejected() {
        assertThat(codes(validator.validate(null))).containsExactly("REQUEST_MISSING");
    }

    @Test
    void emptyRequestReportsEveryRequiredField() {
        List<ValidationError> errors = validator.validate(new TransactionRequest(null, null, null, null, null, null));
        assertThat(codes(errors)).containsExactlyInAnyOrder(
                "TXN_ID_REQUIRED", "ACCOUNT_REQUIRED", "MERCHANT_REQUIRED", "AMOUNT_REQUIRED", "CURRENCY_REQUIRED");
    }

    @Nested
    class TransactionIdRules {

        @ParameterizedTest
        @ValueSource(strings = {"T1", "TXN 100", "TXN_100001", "this-id-is-far-too-long-to-be-accepted-by-the-api"})
        void malformedIdIsRejected(String id) {
            TransactionRequest r = new TransactionRequest(id, "4111111111111111", "MER1001",
                    BigDecimal.TEN, "SGD", null);
            assertThat(codes(validator.validate(r))).containsExactly("TXN_ID_INVALID");
        }

        @Test
        void duplicateIdIsRejected() {
            when(repository.existsByTransactionId("TXN-100001")).thenReturn(true);
            assertThat(codes(validator.validate(valid()))).containsExactly("TXN_DUPLICATE");
        }
    }

    @Nested
    class AccountRules {

        @ParameterizedTest
        @ValueSource(strings = {"12345", "41111111111111112222", "4111-1111-1111", "ABCDEFGHIJ"})
        void wrongFormatIsRejected(String account) {
            TransactionRequest r = new TransactionRequest("TXN-100001", account, "MER1001",
                    BigDecimal.TEN, "SGD", null);
            assertThat(codes(validator.validate(r))).containsExactly("ACCOUNT_INVALID");
        }

        @Test
        void failedChecksumIsRejected() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111112", "MER1001",
                    BigDecimal.TEN, "SGD", null);
            assertThat(codes(validator.validate(r))).containsExactly("ACCOUNT_CHECKSUM");
        }

        @ParameterizedTest
        @ValueSource(strings = {"4111111111111111", "5500005555555559", "1234567897", "378282246310005"})
        void luhnAcceptsKnownGoodNumbers(String number) {
            assertThat(TransactionValidator.passesLuhn(number)).isTrue();
        }
    }

    @Nested
    class AmountRules {

        @ParameterizedTest
        @ValueSource(strings = {"0", "-5.00", "0.001"})
        void tooSmallIsRejected(String amount) {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                    new BigDecimal(amount), "SGD", null);
            assertThat(codes(validator.validate(r))).contains("AMOUNT_TOO_LOW");
        }

        @Test
        void overLimitIsRejected() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                    new BigDecimal("10000.01"), "SGD", null);
            assertThat(codes(validator.validate(r))).containsExactly("AMOUNT_LIMIT_EXCEEDED");
        }

        @Test
        void exactLimitIsAccepted() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                    new BigDecimal("10000.00"), "SGD", null);
            assertThat(validator.validate(r)).isEmpty();
        }

        @Test
        void moreThanTwoDecimalsIsRejected() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                    new BigDecimal("12.345"), "SGD", null);
            assertThat(codes(validator.validate(r))).containsExactly("AMOUNT_PRECISION");
        }
    }

    @Nested
    class CurrencyAndCountryRules {

        @Test
        void unsupportedCurrencyIsRejected() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                    BigDecimal.TEN, "JPY", null);
            assertThat(codes(validator.validate(r))).containsExactly("CURRENCY_UNSUPPORTED");
        }

        @Test
        void lowerCaseCurrencyIsAccepted() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                    BigDecimal.TEN, "usd", null);
            assertThat(validator.validate(r)).isEmpty();
        }

        @Test
        void blockedCountryIsRejected() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                    BigDecimal.TEN, "SGD", "KP");
            assertThat(codes(validator.validate(r))).containsExactly("COUNTRY_BLOCKED");
        }

        @Test
        void invalidCountryCodeIsRejected() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                    BigDecimal.TEN, "SGD", "Singapore");
            assertThat(codes(validator.validate(r))).containsExactly("COUNTRY_INVALID");
        }

        @Test
        void merchantMustBeUpperCaseAlphanumeric() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "mer-1",
                    BigDecimal.TEN, "SGD", null);
            assertThat(codes(validator.validate(r))).containsExactly("MERCHANT_INVALID");
        }
    }

    @Nested
    class RiskAssessment {

        @Test
        void smallAmountIsLowRisk() {
            assertThat(validator.assessRisk(valid())).isEqualTo(ValidationResult.RiskLevel.LOW);
        }

        @Test
        void mediumAmountIsMediumRisk() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                    new BigDecimal("2500.00"), "SGD", null);
            assertThat(validator.assessRisk(r)).isEqualTo(ValidationResult.RiskLevel.MEDIUM);
        }

        @Test
        void largeAmountIsHighRisk() {
            TransactionRequest r = new TransactionRequest("TXN-100001", "4111111111111111", "MER1001",
                    new BigDecimal("5000.00"), "SGD", null);
            assertThat(validator.assessRisk(r)).isEqualTo(ValidationResult.RiskLevel.HIGH);
        }
    }
}
