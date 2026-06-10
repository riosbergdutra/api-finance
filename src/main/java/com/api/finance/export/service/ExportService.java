package com.api.finance.export.service;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.subscription.service.SubscriptionService;
import com.api.finance.transaction.model.Transaction;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final TransactionRepository transactionRepository;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Excel ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] gerarExcel(LocalDate de, LocalDate ate, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        subscriptionService.assertIsPro(userId, "Exportação Excel");

        List<Transaction> transacoes = buscarTransacoes(userId, de, ate);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Transações");

            // ── Cabeçalho ────────────────────────────────────────────────
            CellStyle headerStyle = criarEstiloCabecalho(workbook);
            Row header = sheet.createRow(0);
            String[] colunas = {"Data", "Descrição", "Estabelecimento", "Tipo", "Status", "Valor (R$)", "Categoria"};
            for (int i = 0; i < colunas.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(colunas[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Dados ────────────────────────────────────────────────────
            CellStyle moedaStyle = criarEstiloMoeda(workbook);
            int rowNum = 1;
            for (Transaction trx : transacoes) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(trx.getData().format(BR_DATE));
                row.createCell(1).setCellValue(trx.getDescricao() != null ? trx.getDescricao() : "");
                row.createCell(2).setCellValue(trx.getEstabelecimento() != null ? trx.getEstabelecimento() : "");
                row.createCell(3).setCellValue(trx.getTipo().name());
                row.createCell(4).setCellValue(trx.getStatus().name());

                Cell valorCell = row.createCell(5);
                valorCell.setCellValue(trx.getValor().doubleValue());
                valorCell.setCellStyle(moedaStyle);

                row.createCell(6).setCellValue(
                        trx.getCategory() != null ? trx.getCategory().getNome() : "Sem categoria");
            }

            // Ajusta largura das colunas
            for (int i = 0; i < colunas.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            log.info("[Export] Excel gerado: userId={} transações={}", userId, transacoes.size());
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar Excel: " + e.getMessage(), e);
        }
    }

    // ── PDF ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] gerarPdf(LocalDate de, LocalDate ate, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        subscriptionService.assertIsPro(userId, "Exportação PDF");

        List<Transaction> transacoes = buscarTransacoes(userId, de, ate);

        // TODO: implementar com iText ou OpenPDF
        // Exemplo básico com OpenPDF:
        // Document document = new Document();
        // ByteArrayOutputStream out = new ByteArrayOutputStream();
        // PdfWriter.getInstance(document, out);
        // document.open();
        // document.add(new Paragraph("Relatório de Transações — " + de.format(BR_DATE) + " a " + ate.format(BR_DATE)));
        //
        // PdfPTable table = new PdfPTable(6);
        // // adicionar cabeçalho e linhas...
        //
        // document.add(table);
        // document.close();
        // return out.toByteArray();

        log.info("[Export] PDF solicitado: userId={} de={} ate={} — não implementado", userId, de, ate);
        throw new UnsupportedOperationException(
            "Exportação PDF não implementada. Adicione OpenPDF ou iText ao pom.xml " +
            "e implemente o corpo do método gerarPdf() em ExportService. " +
            "Ver comentários no método para exemplo.");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private List<Transaction> buscarTransacoes(UUID userId, LocalDate de, LocalDate ate) {
        // Busca todas as transações do período (sem paginação — export é completo)
        return transactionRepository.findByUserIdAndPeriodo(
                userId, de, ate, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
    }

    private CellStyle criarEstiloCabecalho(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle criarEstiloMoeda(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }

    private UUID resolveUserId(AuthenticatedUser caller) {
        return userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}
