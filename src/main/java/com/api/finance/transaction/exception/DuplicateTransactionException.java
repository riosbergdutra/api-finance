package com.api.finance.transaction.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateTransactionException extends RuntimeException {
    public DuplicateTransactionException() {
        super("Transação duplicada detectada.");
    }
}
