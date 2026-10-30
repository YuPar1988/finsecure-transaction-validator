package com.finsecure.validator.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {

    boolean existsByTransactionId(String transactionId);
}
