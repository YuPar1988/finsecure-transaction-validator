package com.finsecure.validator.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.finsecure.validator.requestlog.RequestLogAttributes;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationResult> validate(@RequestBody TransactionRequest request,
                                                     HttpServletRequest httpRequest) {
        ValidationResult result = service.validate(request);

        httpRequest.setAttribute(RequestLogAttributes.OUTCOME, result.status().name());
        httpRequest.setAttribute(RequestLogAttributes.TRANSACTION_ID, result.transactionId());

        HttpStatus status = result.approved() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(result);
    }
}
