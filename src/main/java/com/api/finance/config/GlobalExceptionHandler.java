package com.api.finance.config;

import com.api.finance.bff.dto.ErrorResponseDTO;
import com.api.finance.bff.exceptions.AuthIntegrationException;
import com.api.finance.bff.exceptions.SessionNotFoundException;
import com.api.finance.shared.exception.ResourceNotFoundException; // Adicionado
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // NOVO: Tratamento para Recursos não encontrados (User, Account, etc)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleSessionNotFound(SessionNotFoundException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthIntegrationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthIntegration(AuthIntegrationException ex) {
        log.error("Erro de integração com IDP: {}", ex.getMessage());
        return buildResponse("Falha na comunicação com o provedor de identidade.", HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponseDTO> handleRestClientException(RestClientException ex) {
        log.error("Erro na chamada externa: ", ex);
        return buildResponse("O serviço de autenticação está temporariamente indisponível.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex) {
        log.error("Erro não tratado detectado: ", ex);
        return buildResponse("Ocorreu um erro interno no servidor.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponseDTO> buildResponse(String message, HttpStatus status) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                message,
                System.currentTimeMillis(),
                status.value()
        );
        return new ResponseEntity<>(error, status);
    }
}