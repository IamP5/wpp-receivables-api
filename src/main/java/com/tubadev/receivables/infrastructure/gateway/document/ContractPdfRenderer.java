package com.tubadev.receivables.infrastructure.gateway.document;

import com.tubadev.receivables.domain.contract.Contract;
import com.tubadev.receivables.domain.contract.ContractDocument;
import com.tubadev.receivables.domain.contract.ContractDocumentGateway;
import com.tubadev.receivables.domain.contract.Party;
import com.tubadev.receivables.domain.exceptions.InternalErrorException;
import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.utils.InstantUtils;
import com.tubadev.receivables.infrastructure.configuration.properties.ContractProperties;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfGState;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Renders the assignment contract ("instrumento particular de cessão de crédito") as an A4 PDF with OpenPDF.
 * The clauses are a starting point and must be reviewed by the legal team before production use.
 */
@Component
public class ContractPdfRenderer implements ContractDocumentGateway {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm:ss", PT_BR);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT_BR);

    private static final Color INK = new Color(0x1F, 0x29, 0x37);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color ACCENT = new Color(0x0F, 0x5C, 0x4C);
    private static final Color RULE = new Color(0xD1, 0xD5, 0xDB);
    private static final Color SHADE = new Color(0xF3, 0xF4, 0xF6);
    private static final Color WARNING = new Color(0xB4, 0x23, 0x18);

    private static final Font TITLE = new Font(Font.HELVETICA, 15, Font.BOLD, INK);
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, MUTED);
    private static final Font HEADING = new Font(Font.HELVETICA, 9.5f, Font.BOLD, ACCENT);
    private static final Font BODY = new Font(Font.HELVETICA, 9, Font.NORMAL, INK);
    private static final Font BODY_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, INK);
    private static final Font LABEL = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
    private static final Font TABLE_HEADER = new Font(Font.HELVETICA, 8, Font.BOLD, INK);
    private static final Font TABLE_BODY = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, INK);
    private static final Font TABLE_BOLD = new Font(Font.HELVETICA, 8.5f, Font.BOLD, INK);
    private static final Font TABLE_CODE = new Font(Font.COURIER, 7, Font.NORMAL, INK);
    private static final Font LABEL_CELL = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, MUTED);
    private static final Font SMALL = new Font(Font.HELVETICA, 7, Font.NORMAL, MUTED);
    private static final Font BANNER = new Font(Font.HELVETICA, 8.5f, Font.BOLD, WARNING);
    private static final Font WATERMARK = new Font(Font.HELVETICA, 46, Font.BOLD, WARNING);

    private static final Pattern GROUPS_OF_4 = Pattern.compile("(.{4})(?!$)");

    private final ContractProperties properties;

    public ContractPdfRenderer(final ContractProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public ContractDocument render(final Contract aContract) {
        final var out = new ByteArrayOutputStream();
        final var document = new Document(PageSize.A4, 56, 56, 56, 72);

        try {
            final var writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageDecorations(formatCode(aContract.authenticationCode()), properties.disclaimer()));

            document.addTitle("Contrato de cessão de crédito – " + aContract.protocol());
            document.addSubject("Antecipação de recebíveis (boletos)");
            document.addAuthor(Objects.requireNonNullElse(properties.assigneeName(), ""));
            document.addCreator("wpp-receivables-api");
            document.open();

            header(document, aContract);
            if (properties.hasDisclaimer()) {
                banner(document, properties.disclaimer());
            }
            parties(document, aContract.assignor());
            objectClause(document, aContract);
            priceClause(document, aContract);
            declarationsClause(document);
            signatureClause(document, aContract);
            jurisdictionClause(document, aContract);

            document.close();
        } catch (final DocumentException ex) {
            throw InternalErrorException.with("Could not render the contract %s".formatted(aContract.id().value()), ex);
        }

        return new ContractDocument(out.toByteArray(), "contrato-%s.pdf".formatted(aContract.protocol()));
    }

    private void header(final Document document, final Contract aContract) {
        final var table = new PdfPTable(new float[]{3.9f, 2.1f});
        table.setWidthPercentage(100);

        final var title = new PdfPCell();
        title.setBorder(Rectangle.NO_BORDER);
        title.addElement(new Paragraph("Instrumento particular de", SUBTITLE));
        title.addElement(new Paragraph("CESSÃO DE CRÉDITO", TITLE));
        title.addElement(new Paragraph("Antecipação de recebíveis (boletos)", SUBTITLE));
        table.addCell(title);

        final var meta = new PdfPCell();
        meta.setBorder(Rectangle.NO_BORDER);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        meta.addElement(rightAligned(labeled("Protocolo ", aContract.protocol())));
        meta.addElement(rightAligned(labeled("Contrato nº ", aContract.id().value())));
        meta.addElement(rightAligned(labeled("Emitido em ", dateTime(aContract.issuedAt()))));
        table.addCell(meta);

        final var rule = new PdfPCell();
        rule.setColspan(2);
        rule.setBorder(Rectangle.BOTTOM);
        rule.setBorderColor(ACCENT);
        rule.setBorderWidth(1.2f);
        rule.setFixedHeight(6);
        table.addCell(rule);

        document.add(table);
    }

    private void banner(final Document document, final String disclaimer) {
        final var table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);

        final var cell = new PdfPCell(new Phrase(disclaimer.toUpperCase(PT_BR), BANNER));
        cell.setBorderColor(WARNING);
        cell.setBackgroundColor(new Color(0xFD, 0xEC, 0xEA));
        cell.setPadding(7);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);

        document.add(table);
    }

    private void parties(final Document document, final Party assignor) {
        document.add(heading("PARTES"));

        final var table = new PdfPTable(new float[]{1, 1});
        table.setWidthPercentage(100);
        table.addCell(partyCell("CEDENTE", assignor.name(), documentLabel(assignor), "WhatsApp +" + assignor.phoneNumber()));
        table.addCell(partyCell("CESSIONÁRIA", properties.assigneeName(), "CNPJ " + formatDocument(properties.assigneeDocument(), "cnpj"),
                properties.assigneeAddress()));
        document.add(table);

        document.add(paragraph("As partes acima qualificadas celebram o presente instrumento, que se rege pelas cláusulas a seguir."));
    }

    private void objectClause(final Document document, final Contract aContract) {
        document.add(heading("CLÁUSULA 1ª – DO OBJETO"));
        document.add(paragraph("""
                1.1. Pelo presente instrumento, a CEDENTE cede e transfere à CESSIONÁRIA os direitos creditórios \
                representados pelos boletos relacionados no Quadro I ("Créditos Cedidos"), com todos os direitos, \
                garantias e acessórios a eles inerentes."""));

        final var table = new PdfPTable(new float[]{0.35f, 2.3f, 3.1f, 1.1f, 1.35f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setHeaderRows(1);

        for (var header : List.of("#", "Sacado", "Linha digitável", "Vencimento", "Valor de face")) {
            table.addCell(headerCell(header, header.equals("Valor de face") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT));
        }

        var index = 1;
        for (var receivable : aContract.offer().receivables()) {
            table.addCell(bodyCell(String.valueOf(index++), TABLE_BODY, Element.ALIGN_LEFT));
            table.addCell(bodyCell(receivable.payerName(), TABLE_BODY, Element.ALIGN_LEFT));
            table.addCell(bodyCell(Objects.requireNonNullElse(receivable.documentNumber(), "—"), TABLE_CODE, Element.ALIGN_LEFT));
            table.addCell(bodyCell(date(receivable.dueDate()), TABLE_BODY, Element.ALIGN_LEFT));
            table.addCell(bodyCell(money(receivable.amount()), TABLE_BODY, Element.ALIGN_RIGHT));
        }

        final var total = bodyCell("Total (%d boletos)".formatted(aContract.receivablesCount()), TABLE_BOLD, Element.ALIGN_LEFT);
        total.setColspan(4);
        total.setBackgroundColor(SHADE);
        table.addCell(total);
        final var totalValue = bodyCell(money(aContract.offer().grossAmount()), TABLE_BOLD, Element.ALIGN_RIGHT);
        totalValue.setBackgroundColor(SHADE);
        table.addCell(totalValue);

        document.add(caption("Quadro I – Créditos Cedidos"));
        document.add(table);
    }

    private void priceClause(final Document document, final Contract aContract) {
        final var offer = aContract.offer();

        document.add(heading("CLÁUSULA 2ª – DO PREÇO E DO PAGAMENTO"));
        document.add(paragraph("""
                2.1. Em contrapartida à cessão, a CESSIONÁRIA pagará à CEDENTE o valor líquido indicado no Quadro II, \
                resultante da aplicação da taxa de desconto sobre o valor de face dos Créditos Cedidos, proporcionalmente \
                ao prazo até o vencimento de cada boleto."""));
        document.add(paragraph("""
                2.2. O valor líquido será creditado em conta de titularidade da CEDENTE cadastrada na plataforma \
                até a data prevista indicada no Quadro II."""));

        document.add(caption("Quadro II – Condições da operação"));
        document.add(labelValueTable(List.of(
                new String[]{"Valor de face dos Créditos Cedidos", money(offer.grossAmount())},
                new String[]{"Taxa de desconto", percent(offer.monthlyRate()) + " ao mês"},
                new String[]{"Valor do desconto", money(offer.feeAmount())},
                new String[]{"Valor solicitado pela CEDENTE", money(offer.requestedAmount())},
                new String[]{"Valor líquido a receber", money(offer.netAmount())},
                new String[]{"Data prevista do crédito", date(aContract.expectedCreditDate())},
                new String[]{"Último vencimento", date(aContract.lastDueDate())}
        ), 4));
    }

    private void declarationsClause(final Document document) {
        document.add(heading("CLÁUSULA 3ª – DAS DECLARAÇÕES DA CEDENTE"));
        document.add(paragraph("""
                3.1. A CEDENTE declara que os Créditos Cedidos existem, são legítimos, decorrem de operações efetivamente \
                realizadas e estão livres de ônus, gravames ou cessões anteriores, respondendo por sua existência e \
                legitimidade nos termos do art. 295 do Código Civil."""));
        document.add(paragraph("""
                3.2. A CEDENTE autoriza a CESSIONÁRIA a notificar os sacados acerca da cessão, para os fins do art. 290 \
                do Código Civil, e se obriga a repassar à CESSIONÁRIA, em até 2 (dois) dias úteis, qualquer valor que \
                venha a receber referente aos Créditos Cedidos."""));
    }

    private void signatureClause(final Document document, final Contract aContract) {
        final var signature = aContract.signature();

        document.add(heading("CLÁUSULA 4ª – DA CONTRATAÇÃO E ASSINATURA ELETRÔNICAS"));
        document.add(paragraph("""
                4.1. As partes reconhecem como válida e eficaz a contratação por meio eletrônico, realizada pelo canal \
                WhatsApp e confirmada por validação biométrica facial da CEDENTE, nos termos do art. 10, § 2º, da \
                Medida Provisória nº 2.200-2/2001 e da Lei nº 14.063/2020. As evidências da assinatura constam do Quadro III."""));

        document.add(caption("Quadro III – Evidências da assinatura"));
        document.add(labelValueTable(List.of(
                new String[]{"Signatário", "%s (%s)".formatted(aContract.assignor().name(), documentLabel(aContract.assignor()))},
                new String[]{"Canal", "WhatsApp, número +" + signature.phoneNumber()},
                new String[]{"Validação biométrica facial", signature.biometricVerificationId()},
                new String[]{"Score de similaridade", percent(signature.biometricScore())},
                new String[]{"Data e hora da assinatura", dateTime(signature.signedAt()) + " (horário de Brasília)"},
                new String[]{"Código de autenticidade", formatCode(aContract.authenticationCode())}
        ), -1));
    }

    private void jurisdictionClause(final Document document, final Contract aContract) {
        document.add(heading("CLÁUSULA 5ª – DO FORO"));
        document.add(paragraph("5.1. Fica eleito o foro da Comarca de %s para dirimir quaisquer questões oriundas deste instrumento."
                .formatted(properties.city())));

        final var place = new Paragraph("%s, %s.".formatted(properties.city(),
                LONG_DATE.format(aContract.signature().signedAt().atZone(InstantUtils.BUSINESS_ZONE))), BODY);
        place.setSpacingBefore(14);
        document.add(place);

        final var signed = new Paragraph();
        signed.setSpacingBefore(10);
        signed.add(new Chunk("Assinado eletronicamente por ", BODY));
        signed.add(new Chunk(aContract.assignor().name(), BODY_BOLD));
        signed.add(new Chunk(" mediante validação biométrica " + aContract.signature().biometricVerificationId() + ".", BODY));
        document.add(signed);
    }

    // --- building blocks ----------------------------------------------------------------------------------------

    private static Paragraph heading(final String text) {
        final var heading = new Paragraph(text, HEADING);
        heading.setSpacingBefore(14);
        heading.setSpacingAfter(4);
        return heading;
    }

    private static Paragraph paragraph(final String text) {
        final var paragraph = new Paragraph(text, BODY);
        paragraph.setAlignment(Element.ALIGN_JUSTIFIED);
        paragraph.setLeading(12.5f);
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    private static Paragraph caption(final String text) {
        final var caption = new Paragraph(text, LABEL);
        caption.setSpacingBefore(6);
        caption.setSpacingAfter(3);
        return caption;
    }

    private static Paragraph labeled(final String label, final String value) {
        final var paragraph = new Paragraph();
        paragraph.add(new Chunk(label, LABEL));
        paragraph.add(new Chunk(value, BODY_BOLD));
        return paragraph;
    }

    private static Paragraph rightAligned(final Paragraph paragraph) {
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        return paragraph;
    }

    private static PdfPCell partyCell(final String role, final String name, final String document, final String contact) {
        final var cell = new PdfPCell();
        cell.setBorderColor(RULE);
        cell.setPadding(8);
        cell.addElement(new Paragraph(role, LABEL));
        cell.addElement(new Paragraph(Objects.requireNonNullElse(name, "—"), BODY_BOLD));
        cell.addElement(new Paragraph(document, BODY));
        cell.addElement(new Paragraph(Objects.requireNonNullElse(contact, ""), BODY));
        return cell;
    }

    /** Two columns of label/value; {@code highlightRow} (0 based, -1 for none) is printed in bold. */
    private static PdfPTable labelValueTable(final List<String[]> rows, final int highlightRow) {
        final var table = new PdfPTable(new float[]{1.6f, 2.4f});
        table.setWidthPercentage(100);
        table.setKeepTogether(true);
        for (var i = 0; i < rows.size(); i++) {
            final var font = i == highlightRow ? TABLE_BOLD : TABLE_BODY;
            final var label = bodyCell(rows.get(i)[0], i == highlightRow ? TABLE_BOLD : LABEL_CELL, Element.ALIGN_LEFT);
            label.setBackgroundColor(SHADE);
            table.addCell(label);
            table.addCell(bodyCell(rows.get(i)[1], font, Element.ALIGN_LEFT));
        }
        return table;
    }

    private static PdfPCell headerCell(final String text, final int alignment) {
        final var cell = new PdfPCell(new Phrase(text, TABLE_HEADER));
        cell.setBackgroundColor(SHADE);
        cell.setBorderColor(RULE);
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private static PdfPCell bodyCell(final String text, final Font font, final int alignment) {
        final var cell = new PdfPCell(new Phrase(text, font));
        cell.setBorderColor(RULE);
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    // --- formatting ---------------------------------------------------------------------------------------------

    private static String money(final Money money) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(money.amount());
    }

    private static String percent(final BigDecimal rate) {
        final var format = NumberFormat.getPercentInstance(PT_BR);
        format.setMinimumFractionDigits(2);
        return format.format(rate);
    }

    private static String date(final LocalDate date) {
        return DATE.format(date);
    }

    private static String dateTime(final Instant instant) {
        return DATE_TIME.format(instant.atZone(InstantUtils.BUSINESS_ZONE));
    }

    private static String documentLabel(final Party party) {
        if (party.documentNumber() == null) {
            return "documento não informado";
        }
        return "%s %s".formatted(party.documentType().toUpperCase(PT_BR), formatDocument(party.documentNumber(), party.documentType()));
    }

    static String formatDocument(final String digits, final String type) {
        if (digits == null) {
            return "—";
        }
        if ("cnpj".equals(type) && digits.length() == 14) {
            return digits.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        }
        if ("cpf".equals(type) && digits.length() == 11) {
            return digits.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        }
        return digits;
    }

    static String formatCode(final String code) {
        return code == null ? "—" : GROUPS_OF_4.matcher(code).replaceAll("$1-");
    }

    /** Footer with the authentication code and page number, plus the watermark when a disclaimer is set. */
    private static final class PageDecorations extends PdfPageEventHelper {

        private final String authenticationCode;
        private final String disclaimer;

        private PageDecorations(final String authenticationCode, final String disclaimer) {
            this.authenticationCode = authenticationCode;
            this.disclaimer = disclaimer;
        }

        @Override
        public void onEndPage(final PdfWriter writer, final Document document) {
            final var footer = writer.getDirectContent();
            final var left = document.left();
            final var right = document.right();
            final var y = document.bottom() - 30;

            ColumnText.showTextAligned(footer, Element.ALIGN_LEFT,
                    new Phrase("Código de autenticidade: " + authenticationCode, SMALL), left, y, 0);
            ColumnText.showTextAligned(footer, Element.ALIGN_RIGHT,
                    new Phrase("Página " + writer.getPageNumber(), SMALL), right, y, 0);

            if (disclaimer != null && !disclaimer.isBlank()) {
                final var under = writer.getDirectContentUnder();
                under.saveState();
                final var state = new PdfGState();
                state.setFillOpacity(0.07f);
                under.setGState(state);
                ColumnText.showTextAligned(under, Element.ALIGN_CENTER, new Phrase("SEM VALIDADE JURÍDICA", WATERMARK),
                        (document.right() + document.left()) / 2, (document.top() + document.bottom()) / 2, 45);
                under.restoreState();
            }
        }
    }
}
