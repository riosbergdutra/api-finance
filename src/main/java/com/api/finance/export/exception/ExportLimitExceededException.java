package com.api.finance.export.exception;

/** Sinaliza que a geração excederia o limite de memória configurado para exportações. */
public class ExportLimitExceededException extends RuntimeException {

    public ExportLimitExceededException(String message) {
        super(message);
    }
}
