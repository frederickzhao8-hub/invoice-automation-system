package com.invoiceautomation.backend.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PdfTextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractionService.class);
    private static final Set<String> PDF_CONTENT_TYPES = Set.of("application/pdf");
    private static final int LOW_TEXT_WARNING_THRESHOLD = 24;

    public String extractText(MultipartFile file) {
        validatePdf(file);

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            String extractedText = textStripper.getText(document)
                    .replace("\u0000", "")
                    .trim();

            if (extractedText.isBlank()) {
                log.warn("No extractable text found in PDF file: {}", file.getOriginalFilename());
                return "";
            }

            if (extractedText.length() < LOW_TEXT_WARNING_THRESHOLD) {
                log.warn(
                        "Very little text was extracted from PDF file: {} ({} chars)",
                        file.getOriginalFilename(),
                        extractedText.length());
            } else {
                log.info(
                        "Extracted {} characters of text from PDF file: {}",
                        extractedText.length(),
                        file.getOriginalFilename());
            }

            return extractedText;
        } catch (IOException exception) {
            log.warn("Unable to extract text from PDF file: {}", file.getOriginalFilename(), exception);
            return "";
        }
    }

    private void validatePdf(MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);

        if (!fileName.endsWith(".pdf") && !PDF_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Bulk import only supports PDF files.");
        }
    }
}
