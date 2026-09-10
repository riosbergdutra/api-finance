package com.api.finance.export.controller;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.export.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Exportação de transações — Excel e PDF.
 *
 * REQUER PLANO PRO: verificado no ExportService via SubscriptionService.assertIsPro()
 *
 * ENDPOINTS:
 *   GET /export/excel?de=2024-01-01&ate=2024-01-31
 *   GET /export/pdf?de=2024-01-01&ate=2024-01-31
 */
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final AuthenticatedUserProvider userProvider;

    @GetMapping("/excel")
    public ResponseEntity<ByteArrayResource> exportarExcel(
            @RequestParam LocalDate de,
            @RequestParam LocalDate ate,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        byte[] bytes = exportService.gerarExcel(de, ate, caller);

        String filename = "transacoes-" + de.format(DateTimeFormatter.ISO_DATE)
                + "-a-" + ate.format(DateTimeFormatter.ISO_DATE) + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/pdf")
    public ResponseEntity<ByteArrayResource> exportarPdf(
            @RequestParam LocalDate de,
            @RequestParam LocalDate ate,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        byte[] bytes = exportService.gerarPdf(de, ate, caller);

        String filename = "relatorio-" + de.format(DateTimeFormatter.ISO_DATE)
                + "-a-" + ate.format(DateTimeFormatter.ISO_DATE) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(bytes));
    }
}
