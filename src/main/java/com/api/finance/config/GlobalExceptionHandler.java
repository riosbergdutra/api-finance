package com.api.finance.config;

import com.api.finance.account.exception.AccountNotFoundException;
import com.api.finance.account.exception.DuplicateAccountNameException;
import com.api.finance.export.exception.ExportLimitExceededException;
import com.api.finance.bff.dto.ErrorResponseDTO;
import com.api.finance.bff.exceptions.AuthIntegrationException;
import com.api.finance.bff.exceptions.SessionNotFoundException;
import com.api.finance.budget.exception.BudgetNotFoundException;
import com.api.finance.category.exception.CategoryNotFoundException;
import com.api.finance.goal.exception.GoalNotFoundException;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.transaction.exception.TransactionNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import com.api.finance.subscription.exception.PlanLimitExceededException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 400 ────────────────────────────────────────────────────────────────

    /** Bean Validation (@Valid) — agrega todos os erros de campo */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildResponse(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ExportLimitExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleExportLimit(ExportLimitExceededException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotImplemented(UnsupportedOperationException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.NOT_IMPLEMENTED);
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handlePlanLimit(PlanLimitExceededException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.PAYMENT_REQUIRED);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalState(IllegalStateException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // ── 401 ────────────────────────────────────────────────────────────────

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleSessionNotFound(SessionNotFoundException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    /** JWT inválido, expirado ou mal-formado */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponseDTO> handleJwtException(JwtException ex) {
        log.warn("JWT inválido: {}", ex.getMessage());
        return buildResponse("Token inválido ou expirado.", HttpStatus.UNAUTHORIZED);
    }

    // ── 403 ────────────────────────────────────────────────────────────────

    /** Resposta deliberadamente vaga — não revela estrutura interna */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse("Acesso negado.", HttpStatus.FORBIDDEN);
    }

    // ── 404 ────────────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccountNotFound(AccountNotFoundException ex) {
        // Mensagem vaga intencionalmente — não confirma existência do recurso para outros usuários
        return buildResponse("Conta não encontrada.", HttpStatus.NOT_FOUND);
    }

    /*
     * FIX: as 4 exceptions abaixo já têm @ResponseStatus(NOT_FOUND) na própria classe,
     * então o Spring devolvia 404 mesmo sem handler aqui — mas usando o formato de erro
     * padrão do Spring Boot (campos timestamp/status/error/path), diferente do
     * ErrorResponseDTO usado em todo o resto da API. Isso quebrava qualquer client que
     * esperasse um contrato de erro único. Os handlers abaixo padronizam a resposta.
     */
    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleCategoryNotFound(CategoryNotFoundException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(GoalNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleGoalNotFound(GoalNotFoundException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BudgetNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleBudgetNotFound(BudgetNotFoundException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleTransactionNotFound(TransactionNotFoundException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    // ── 409 ────────────────────────────────────────────────────────────────

    @ExceptionHandler(DuplicateAccountNameException.class)
    public ResponseEntity<ErrorResponseDTO> handleDuplicateAccountName(DuplicateAccountNameException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.CONFLICT);
    }

    // ── 502 / 503 ──────────────────────────────────────────────────────────

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

    // ── 500 ────────────────────────────────────────────────────────────────

    /** Catch-all — nunca expõe stack trace para o cliente */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex) {
        log.error("Erro não tratado: ", ex);
        return buildResponse("Ocorreu um erro interno no servidor.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponseDTO> buildResponse(String message, HttpStatus status) {
        return new ResponseEntity<>(
                new ErrorResponseDTO(message, System.currentTimeMillis(), status.value()),
                status
        );
    }
}