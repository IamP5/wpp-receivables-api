package com.tubadev.receivables.infrastructure.gateway.document;

import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.infrastructure.configuration.properties.ContractProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.nio.charset.StandardCharsets;

@Tag("unitTest")
class ContractPdfRendererTest {

    private static final ContractProperties PROPERTIES = new ContractProperties("Cessionária Exemplo S.A.", "12345678000195",
            "Av. Paulista, 1000 – São Paulo/SP", "São Paulo", "Ambiente de desenvolvimento – sem validade jurídica");

    @Test
    void givenContract_whenRendering_shouldPrintTermsBoletosAndSignatureEvidence() throws Exception {
        final var aContract = Fixture.Contracts.issued(Fixture.Customers.maria(), new ConversationId("conv_1"));

        final var document = new ContractPdfRenderer(PROPERTIES).render(aContract);

        Assertions.assertEquals("contrato-%s.pdf".formatted(aContract.protocol()), document.filename());
        Assertions.assertEquals("%PDF", new String(document.content(), 0, 4, StandardCharsets.US_ASCII));

        final var text = textOf(document.content());
        Assertions.assertTrue(text.contains(aContract.protocol()), text);
        Assertions.assertTrue(text.contains("Maria Silva"));
        Assertions.assertTrue(text.contains("CNPJ 11.222.333/0001-81"));
        Assertions.assertTrue(text.contains("CNPJ 12.345.678/0001-95"));
        Assertions.assertTrue(text.contains("Mercado A"));
        Assertions.assertTrue(text.contains("R$ 10.246,27"));
        Assertions.assertTrue(text.contains("1,99% ao mês"));
        Assertions.assertTrue(text.contains("02/10/2026"));
        Assertions.assertTrue(text.contains("bio_123"));
        Assertions.assertTrue(text.contains("97,00%"));
        Assertions.assertTrue(text.contains("01/10/2026 às 12:00:00"), "signature time in Brasília");
        Assertions.assertTrue(text.contains(aContract.authenticationCode().substring(0, 4) + "-"));
        Assertions.assertTrue(text.contains("AMBIENTE DE DESENVOLVIMENTO"));
    }

    @Test
    void givenNoDisclaimer_whenRendering_shouldOmitBanner() throws Exception {
        final var properties = new ContractProperties("Cessionária Exemplo S.A.", "12345678000195", "Rua A, 1", "São Paulo", "");
        final var aContract = Fixture.Contracts.issued(Fixture.Customers.maria(), new ConversationId("conv_1"));

        final var text = textOf(new ContractPdfRenderer(properties).render(aContract).content());

        Assertions.assertFalse(text.contains("SEM VALIDADE"));
    }

    @Test
    void givenAuthenticationCode_whenFormatting_shouldGroupBy4() {
        Assertions.assertEquals("ABCD-EF01-23", ContractPdfRenderer.formatCode("ABCDEF0123"));
        Assertions.assertEquals("123.456.789-09", ContractPdfRenderer.formatDocument("12345678909", "cpf"));
    }

    private static String textOf(final byte[] pdf) throws Exception {
        try (var reader = new PdfReader(pdf)) {
            final var extractor = new PdfTextExtractor(reader);
            final var text = new StringBuilder();
            for (var page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            // pt-BR currency uses a no-break space, which the extractor may surround with extra spaces
            return text.toString().replaceAll("[\\s\u00A0]+", " ");
        }
    }
}
