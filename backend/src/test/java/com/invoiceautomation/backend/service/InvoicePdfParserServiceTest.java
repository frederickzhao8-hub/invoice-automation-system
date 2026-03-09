package com.invoiceautomation.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.invoiceautomation.backend.entity.InvoiceParseStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class InvoicePdfParserServiceTest {

    private final InvoicePdfParserService parserService = new InvoicePdfParserService();

    @Test
    void parsesSpanishCfdiLabels() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "cfdi-spanish.pdf",
                "application/pdf",
                createPdf(
                        "Proveedor: Comercializadora Demo SA de CV",
                        "Factura No: CFDI-1001",
                        "Fecha: 2026-03-01",
                        "Cantidad: 2",
                        "Valor unitario: MXN 50.00",
                        "Subtotal: MXN 100.00",
                        "IVA 16%: MXN 16.00",
                        "Total: MXN 116.00",
                        "Moneda: MXN"));

        ParsedInvoiceData parsedInvoiceData = parserService.parse(file);

        assertEquals("CFDI-1001", parsedInvoiceData.invoiceNumber());
        assertEquals("Comercializadora Demo SA de CV", parsedInvoiceData.vendorName());
        assertEquals(new BigDecimal("2.00"), parsedInvoiceData.quantity());
        assertEquals(new BigDecimal("50.00"), parsedInvoiceData.unitPrice());
        assertEquals(new BigDecimal("100.00"), parsedInvoiceData.subtotalAmount());
        assertEquals(new BigDecimal("16.00"), parsedInvoiceData.taxAmount());
        assertEquals(new BigDecimal("116.00"), parsedInvoiceData.totalAmount());
        assertEquals("MXN", parsedInvoiceData.currency());
        assertEquals(InvoiceParseStatus.SUCCESS, parsedInvoiceData.parseStatus());
    }

    @Test
    void keepsEnglishLabelSupport() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "invoice-english.pdf",
                "application/pdf",
                createPdf(
                        "Vendor: Acme Supplies",
                        "Invoice Number: INV-10024",
                        "Issue Date: 2026-03-01",
                        "Quantity: 3",
                        "Unit Price: USD 40.00",
                        "Subtotal: USD 120.00",
                        "Tax: USD 9.60",
                        "Total: USD 129.60",
                        "Currency: USD"));

        ParsedInvoiceData parsedInvoiceData = parserService.parse(file);

        assertEquals("INV-10024", parsedInvoiceData.invoiceNumber());
        assertEquals("Acme Supplies", parsedInvoiceData.vendorName());
        assertEquals(new BigDecimal("3.00"), parsedInvoiceData.quantity());
        assertEquals(new BigDecimal("40.00"), parsedInvoiceData.unitPrice());
        assertEquals(new BigDecimal("120.00"), parsedInvoiceData.subtotalAmount());
        assertEquals(new BigDecimal("9.60"), parsedInvoiceData.taxAmount());
        assertEquals(new BigDecimal("129.60"), parsedInvoiceData.totalAmount());
        assertEquals("USD", parsedInvoiceData.currency());
        assertEquals(InvoiceParseStatus.SUCCESS, parsedInvoiceData.parseStatus());
    }

    @Test
    void parsesMexicanCfdiSingleLineItemLayout() {
        ParsedInvoiceData parsedInvoiceData = parserService.parseText(String.join("\n",
                "INGRESO WHFMX_5089",
                "C78E5DE6-EBBC-40F8-A4CB-A03D003C45D9",
                "G01 - Adquisicion de mercancias",
                "A1",
                "AME900814LM3",
                "Uso de CFDI",
                "Fecha de emision",
                "13/03/2025 01:37:36 p. m.",
                "UUID",
                "WUHAN FIBERHOME INTERNATIONAL DE",
                "MEXICO",
                "WFI180528QL6",
                "Orden de compra 49R-A31934-427",
                "Cantidad Clave Descripcion   ImporteValor unitarioUnidad Descuento",
                "Obj",
                "Imp",
                "20000 26121607 MTR - Metro CABLE FIBRA DE 24 FIG 8  (1/16) $0.37 $7,320.0002",
                "Impuesto Trasladado IMPUESTO: 002 - IVA TIPO FACTOR: Tasa TASA O CUOTA: 16.00 % IMPORTE: $1,171.20",
                "Subtotal $7,320.00",
                "$1,171.20IVA 16.00 %",
                "OCHO MIL CUATROCIENTOS NOVENTA Y UN DOLARES 20/100 USD $8,491.20Total",
                "Metodo de pago Moneda / Tipo de cambio",
                "PPD - Pago en parcialidades o diferido USD 20.1828/"));

        assertEquals("WHFMX_5089", parsedInvoiceData.invoiceNumber());
        assertEquals("A1", parsedInvoiceData.vendorName());
        assertEquals(new BigDecimal("20000.00"), parsedInvoiceData.quantity());
        assertEquals(new BigDecimal("0.37"), parsedInvoiceData.unitPrice());
        assertEquals(new BigDecimal("7320.00"), parsedInvoiceData.subtotalAmount());
        assertEquals(new BigDecimal("1171.20"), parsedInvoiceData.taxAmount());
        assertEquals(new BigDecimal("8491.20"), parsedInvoiceData.totalAmount());
        assertEquals("USD", parsedInvoiceData.currency());
        assertEquals(InvoiceParseStatus.SUCCESS, parsedInvoiceData.parseStatus());
    }

    @Test
    void parsesMexicanCfdiMultiLineItemLayout() {
        ParsedInvoiceData parsedInvoiceData = parserService.parseText(String.join("\n",
                "INGRESO WHFMX_5265",
                "1C5CCFFC-DE42-47F0-83D6-C99C09B872D7",
                "G01 - Adquisicion de mercancias",
                "A1",
                "AME900814LM3",
                "Uso de CFDI",
                "Fecha de emision",
                "07/04/2025 10:23:00 a. m.",
                "UUID",
                "WUHAN FIBERHOME INTERNATIONAL DE",
                "MEXICO",
                "WFI180528QL6",
                "Orden de compra 49R-A31934-427",
                "Cantidad Clave Descripcion   ImporteValor unitarioUnidad Descuento",
                "Obj",
                "Imp",
                "80000 26121607 MTR - Metro CABLE ADSS 96-F SJ. Fecha de Pago de Pedimento",
                "04/04/2025.",
                "$0.76 $60,800.0002",
                "Impuesto Trasladado IMPUESTO: 002 - IVA TIPO FACTOR: Tasa TASA O CUOTA: 16.00 % IMPORTE: $9,728.00",
                "Informacion aduanera",
                "Subtotal $60,800.00",
                "$9,728.00IVA 16.00 %",
                "SETENTA MIL QUINIENTOS VEINTIOCHO DOLARES 00/100 USD $70,528.00Total",
                "Metodo de pago Moneda / Tipo de cambio",
                "PPD - Pago en parcialidades o diferido USD 20.501/"));

        assertEquals("WHFMX_5265", parsedInvoiceData.invoiceNumber());
        assertEquals("A1", parsedInvoiceData.vendorName());
        assertEquals(new BigDecimal("80000.00"), parsedInvoiceData.quantity());
        assertEquals(new BigDecimal("0.76"), parsedInvoiceData.unitPrice());
        assertEquals(new BigDecimal("60800.00"), parsedInvoiceData.subtotalAmount());
        assertEquals(new BigDecimal("9728.00"), parsedInvoiceData.taxAmount());
        assertEquals(new BigDecimal("70528.00"), parsedInvoiceData.totalAmount());
        assertEquals("USD", parsedInvoiceData.currency());
        assertEquals(InvoiceParseStatus.SUCCESS, parsedInvoiceData.parseStatus());
    }

    @Test
    void prefersKnownCustomersCaseInsensitively() {
        ParsedInvoiceData totalBoxInvoice = parserService.parseText(String.join("\n",
                "INGRESO BOX_3918",
                "G01 - Adquisicion de mercancias",
                "T1",
                "TBO140305DH0",
                "Uso de CFDI",
                "UUID",
                "WUHAN FIBERHOME INTERNATIONAL DE MEXICO",
                "WFI180528QL6",
                "Cantidad Clave Descripcion   ImporteValor unitarioUnidad Descuento",
                "300 43223300 PZA - Caja metalica $42.00 $12,600.00",
                "Subtotal $12,600.00",
                "$2,016.00IVA 16.00 %",
                "USD $14,616.00Total"));

        ParsedInvoiceData totalPlayInvoice = parserService.parseText(String.join("\n",
                "INGRESO TP_3866",
                "G01 - Adquisicion de mercancias",
                "T2",
                "TPT890516JP5",
                "Uso de CFDI",
                "UUID",
                "WUHAN FIBERHOME INTERNATIONAL DE MEXICO",
                "WFI180528QL6",
                "Cantidad Clave Descripcion   ImporteValor unitarioUnidad Descuento",
                "8280 43222609 PZA - ONU XPON $42.00 $347,760.00",
                "Subtotal $347,760.00",
                "$55,641.60IVA 16.00 %",
                "USD $403,401.60Total"));

        assertEquals("T1", totalBoxInvoice.vendorName());
        assertEquals("T2", totalPlayInvoice.vendorName());
    }

    private byte[] createPdf(String... lines) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.setLeading(16);
                contentStream.newLineAtOffset(48, 740);

                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLine();
                }

                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
