package com.api.finance.export.service;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.subscription.service.SubscriptionService;
import com.api.finance.transaction.model.Transaction;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.user.application.port.UserDirectoryPort;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
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
    private final UserDirectoryPort userDirectory;

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
                org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
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

                org.apache.poi.ss.usermodel.Cell valorCell = row.createCell(5);
                valorCell.setCellValue(trx.getValor().doubleValue());
                valorCell.setCellStyle(moedaStyle);

                row.createCell(6).setCellValue(
                        trx.getCategory() != null ? trx.getCategory().getNome() : "Sem categoria");
            }

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

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(out);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc)) {

            document.add(new Paragraph("Relatório de Transações")
                    .setBold()
                    .setFontSize(18));

            document.add(new Paragraph(
                    "Período: " + de.format(BR_DATE) + " a " + ate.format(BR_DATE))
                    .setFontSize(10)
                    .setFontColor(ColorConstants.GRAY)
                    .setMarginBottom(15));

            BigDecimal totalReceitas = somarPorTipo(transacoes, com.api.finance.transaction.model.TransactionType.RECEITA);
            BigDecimal totalDespesas = somarPorTipo(transacoes, com.api.finance.transaction.model.TransactionType.DESPESA);
            BigDecimal saldo = totalReceitas.subtract(totalDespesas);

            Table resumo = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                    .useAllAvailableWidth()
                    .setMarginBottom(20);
            resumo.addCell(celulaResumo("Receitas", totalReceitas, new DeviceRgb(22, 163, 74)));
            resumo.addCell(celulaResumo("Despesas", totalDespesas, new DeviceRgb(220, 38, 38)));
            resumo.addCell(celulaResumo("Saldo", saldo,
                    saldo.signum() < 0 ? new DeviceRgb(220, 38, 38) : new DeviceRgb(22, 163, 74)));
            document.add(resumo);

            float[] larguras = {1.5f, 3f, 2.5f, 1.5f, 1.5f, 1.8f, 2f};
            Table tabela = new Table(UnitValue.createPercentArray(larguras)).useAllAvailableWidth();

            String[] colunas = {"Data", "Descrição", "Estabelecimento", "Tipo", "Status", "Valor (R$)", "Categoria"};
            for (String coluna : colunas) {
                tabela.addHeaderCell(
                        new Cell().add(new Paragraph(coluna).setBold().setFontColor(ColorConstants.WHITE))
                                .setBackgroundColor(new DeviceRgb(30, 41, 59))
                                .setPadding(5));
            }

            for (Transaction trx : transacoes) {
                boolean despesa = trx.getTipo() == com.api.finance.transaction.model.TransactionType.DESPESA;
                tabela.addCell(celula(trx.getData().format(BR_DATE)));
                tabela.addCell(celula(trx.getDescricao() != null ? trx.getDescricao() : ""));
                tabela.addCell(celula(trx.getEstabelecimento() != null ? trx.getEstabelecimento() : ""));
                tabela.addCell(celula(trx.getTipo().name()));
                tabela.addCell(celula(trx.getStatus().name()));
                tabela.addCell(
                        new Cell().add(new Paragraph((despesa ? "-" : "") + formatarMoeda(trx.getValor()))
                                        .setFontColor(despesa ? new DeviceRgb(220, 38, 38) : new DeviceRgb(22, 163, 74)))
                                .setTextAlignment(TextAlignment.RIGHT)
                                .setPadding(4)
                                .setBorder(Border.NO_BORDER));
                tabela.addCell(celula(trx.getCategory() != null ? trx.getCategory().getNome() : "Sem categoria"));
            }

            document.add(tabela);

            if (transacoes.isEmpty()) {
                document.add(new Paragraph("Nenhuma transação encontrada no período selecionado.")
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(10));
            }

            document.close();
            log.info("[Export] PDF gerado: userId={} transações={}", userId, transacoes.size());
            return out.toByteArray();

        } catch (Exception e) {
            log.error("[Export] Erro ao gerar PDF: userId={}", userId, e);
            throw new RuntimeException("Erro ao gerar PDF: " + e.getMessage(), e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private List<Transaction> buscarTransacoes(UUID userId, LocalDate de, LocalDate ate) {
        return transactionRepository.findByUserIdAndPeriodo(
                userId, de, ate, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
    }

    private BigDecimal somarPorTipo(List<Transaction> transacoes, com.api.finance.transaction.model.TransactionType tipo) {
        return transacoes.stream()
                .filter(t -> t.getTipo() == tipo)
                .filter(t -> t.getStatus() == com.api.finance.transaction.model.TransactionStatus.CONFIRMADA)
                .map(Transaction::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String formatarMoeda(BigDecimal valor) {
        return String.format(new java.util.Locale("pt", "BR"), "R$ %,.2f", valor);
    }

    private Cell celula(String texto) {
        return new Cell().add(new Paragraph(texto)).setPadding(4).setBorder(Border.NO_BORDER);
    }

    private Cell celulaResumo(String label, BigDecimal valor, DeviceRgb cor) {
        return new Cell()
                .add(new Paragraph(label).setFontSize(9).setFontColor(ColorConstants.GRAY))
                .add(new Paragraph(formatarMoeda(valor)).setBold().setFontColor(cor).setFontSize(13))
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setTextAlignment(TextAlignment.CENTER)
                .setBackgroundColor(new DeviceRgb(248, 250, 252))
                .setPadding(10)
                .setBorder(Border.NO_BORDER);
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

    /** Resolve a identidade externa para o UUID interno usado pela persistência. */
    private UUID resolveUserId(AuthenticatedUser caller) {
        return userDirectory.findInternalUserId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}