package com.api.finance.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@Getter
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final Object fieldValue;

    private ResourceNotFoundException(String resourceName, Object fieldValue) {
        super(String.format("%s não encontrado com: %s", resourceName, fieldValue));
        this.resourceName = resourceName;
        this.fieldValue = fieldValue;
    }

    public static ResourceNotFoundException of(String resourceName, UUID id) {
        return new ResourceNotFoundException(resourceName, id);
    }
}