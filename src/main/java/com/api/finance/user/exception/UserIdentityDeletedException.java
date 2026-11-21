package com.api.finance.user.exception;

/** Prevents an erased identity from silently recreating a new local account. */
public class UserIdentityDeletedException extends RuntimeException {
    public UserIdentityDeletedException() {
        super("Esta identidade já teve a conta FinanceFlow excluída.");
    }
}
