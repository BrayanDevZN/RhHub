package br.rhhub.infra.document;

import br.rhhub.application.port.out.DocumentTextExtractor;
import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.DocumentType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class PdfDocxTextExtractor implements DocumentTextExtractor {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};

    @Override
    public Optional<DocumentType> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, PDF_MAGIC)) {
            return Optional.of(DocumentType.PDF);
        }
        if (startsWith(content, ZIP_MAGIC) && hasWordEntry(content)) {
            return Optional.of(DocumentType.DOCX);
        }
        return Optional.empty();
    }

    @Override
    public String extract(byte[] content, DocumentType type) {
        try {
            return switch (type) {
                case PDF -> pdfText(content);
                case DOCX -> docxText(content);
            };
        } catch (IOException | RuntimeException e) {
            throw new DomainException.Unprocessable("Não foi possível ler o arquivo " + type.name(), e);
        }
    }

    private static String pdfText(byte[] content) throws IOException {
        try (var doc = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(doc).strip();
        }
    }

    private static String docxText(byte[] content) throws IOException {
        try (var doc = new XWPFDocument(new ByteArrayInputStream(content));
                var extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText().strip();
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Um .docx é um zip com word/document.xml; outros zips (xlsx, jar...) não contam. */
    private static boolean hasWordEntry(byte[] content) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
