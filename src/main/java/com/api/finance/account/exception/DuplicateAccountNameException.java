package com.api.finance.account.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateAccountNameException extends RuntimeException {
    public DuplicateAccountNameException(String name) {
        super("Já existe uma conta com o nome: " + name);
    }
}
