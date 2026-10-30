package com.finsecure.validator.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.finsecure.validator.config.ValidationProperties;

/**
 * Business rules applied to every incoming transaction.
 * The validator only reports problems; persisting and metrics are handled by {@link TransactionService}.
 */
@Component
public class TransactionValidator {

    private static final Pattern TRANSACTION_ID = Pattern.compile("^[A-Za-z0-9-]{6,40}$");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^\\d{10,16}$");
    private static final Pattern MERCHANT_ID = Pattern.compile("^[A-Z0-9]{4,20}$");
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

    private final ValidationProperties properties;
    private final TransactionRecordRepository repository;

    public TransactionValidator(ValidationProperties properties, TransactionRecordRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public List<ValidationError> validate(TransactionRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        if (request == null) {
            errors.add(new ValidationError("REQUEST_MISSING", "body", "Request body is required"));
            return errors;
        }

        checkTransactionId(request.transactionId(), errors);
        checkAccount(request.accountNumber(), errors);
        checkMerchant(request.merchantId(), errors);
        checkAmount(request.amount(), errors);
        checkCurrency(request.currency(), errors);
        checkCountry(request.country(), errors);

        return errors;
    }

    public ValidationResult.RiskLevel assessRisk(TransactionRequest request) {
        BigDecimal amount = request.amount();
        if (amount == null) {
            return ValidationResult.RiskLevel.LOW;
        }
        if (amount.compareTo(properties.highRiskAmount()) >= 0) {
            return ValidationResult.RiskLevel.HIGH;
        }
        if (amount.compareTo(properties.highRiskAmount().divide(BigDecimal.valueOf(2))) >= 0) {
            return ValidationResult.RiskLevel.MEDIUM;
        }
        return ValidationResult.RiskLevel.LOW;
    }

    private void checkTransactionId(String transactionId, List<ValidationError> errors) {
        if (isBlank(transactionId)) {
            errors.add(new ValidationError("TXN_ID_REQUIRED", "transactionId", "Transaction ID is required"));
        } else if (!TRANSACTION_ID.matcher(transactionId).matches()) {
            errors.add(new ValidationError("TXN_ID_INVALID", "transactionId",
                    "Transaction ID must be 6-40 letters, digits or hyphens"));
        } else if (repository.existsByTransactionId(transactionId)) {
            errors.add(new ValidationError("TXN_DUPLICATE", "transactionId",
                    "Transaction " + transactionId + " has already been processed"));
        }
    }

    private void checkAccount(String accountNumber, List<ValidationError> errors) {
        if (isBlank(accountNumber)) {
            errors.add(new ValidationError("ACCOUNT_REQUIRED", "accountNumber", "Account number is required"));
        } else if (!ACCOUNT_NUMBER.matcher(accountNumber).matches()) {
            errors.add(new ValidationError("ACCOUNT_INVALID", "accountNumber",
                    "Account number must contain 10-16 digits"));
        } else if (!passesLuhn(accountNumber)) {
            errors.add(new ValidationError("ACCOUNT_CHECKSUM", "accountNumber",
                    "Account number failed checksum verification"));
        }
    }

    private void checkMerchant(String merchantId, List<ValidationError> errors) {
        if (isBlank(merchantId)) {
            errors.add(new ValidationError("MERCHANT_REQUIRED", "merchantId", "Merchant ID is required"));
        } else if (!MERCHANT_ID.matcher(merchantId).matches()) {
            errors.add(new ValidationError("MERCHANT_INVALID", "merchantId",
                    "Merchant ID must be 4-20 upper-case letters or digits"));
        }
    }

    private void checkAmount(BigDecimal amount, List<ValidationError> errors) {
        if (amount == null) {
            errors.add(new ValidationError("AMOUNT_REQUIRED", "amount", "Amount is required"));
            return;
        }
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            errors.add(new ValidationError("AMOUNT_PRECISION", "amount", "Amount cannot have more than 2 decimal places"));
        }
        if (amount.compareTo(properties.minAmount()) < 0) {
            errors.add(new ValidationError("AMOUNT_TOO_LOW", "amount",
                    "Amount must be at least " + properties.minAmount()));
        } else if (amount.compareTo(properties.maxAmount()) > 0) {
            errors.add(new ValidationError("AMOUNT_LIMIT_EXCEEDED", "amount",
                    "Amount exceeds the single transaction limit of " + properties.maxAmount()));
        }
    }

    private void checkCurrency(String currency, List<ValidationError> errors) {
        if (isBlank(currency)) {
            errors.add(new ValidationError("CURRENCY_REQUIRED", "currency", "Currency is required"));
        } else if (!properties.allowedCurrencies().contains(currency.toUpperCase(Locale.ROOT))) {
            errors.add(new ValidationError("CURRENCY_UNSUPPORTED", "currency",
                    "Currency " + currency + " is not supported. Allowed: " + properties.allowedCurrencies()));
        }
    }

    private void checkCountry(String country, List<ValidationError> errors) {
        if (isBlank(country)) {
            return;
        }
        if (!COUNTRY.matcher(country).matches()) {
            errors.add(new ValidationError("COUNTRY_INVALID", "country", "Country must be a 2-letter ISO code"));
        } else if (properties.blockedCountries().contains(country)) {
            errors.add(new ValidationError("COUNTRY_BLOCKED", "country",
                    "Transactions from " + country + " are blocked by compliance policy"));
        }
    }

    static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (doubleIt) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
