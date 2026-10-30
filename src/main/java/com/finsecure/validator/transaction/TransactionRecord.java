package com.finsecure.validator.transaction;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 40)
    private String transactionId;

    @Column(name = "account_masked", length = 20)
    private String accountMasked;

    @Column(name = "merchant_id", length = 20)
    private String merchantId;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 10)
    private ValidationResult.RiskLevel riskLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransactionRecord() {
    }

    public TransactionRecord(TransactionRequest request, ValidationResult.RiskLevel riskLevel, Instant createdAt) {
        this.transactionId = request.transactionId();
        this.accountMasked = Masking.maskAccount(request.accountNumber());
        this.merchantId = request.merchantId();
        this.amount = request.amount();
        this.currency = request.currency();
        this.country = request.country();
        this.riskLevel = riskLevel;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountMasked() {
        return accountMasked;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCountry() {
        return country;
    }

    public ValidationResult.RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
