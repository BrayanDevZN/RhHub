package br.rhhub.infra.document;

import br.rhhub.testing.UnitTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.rhhub.domain.exception.DomainException;
import br.rhhub.domain.model.DocumentType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

@UnitTest
class PdfDocxTextExtractorTest {

    private final PdfDocxTextExtractor extractor = new PdfDocxTextExtractor();

    @Test
    void detectsAndReadsPdf() throws Exception {
        byte[] pdf = pdf("Curriculo Java Spring");
        assertThat(extractor.detect(pdf)).contains(DocumentType.PDF);
        assertThat(extractor.extract(pdf, DocumentType.PDF)).contains("Curriculo Java Spring");
    }

    @Test
    void detectsAndReadsDocx() throws Exception {
        byte[] docx = docx("Experiencia com Kafka");
        assertThat(extractor.detect(docx)).contains(DocumentType.DOCX);
        assertThat(extractor.extract(docx, DocumentType.DOCX)).contains("Experiencia com Kafka");
    }

    @Test
    void ignoresDeclaredTypeAndUnknownContent() {
        assertThat(extractor.detect("texto puro".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(extractor.detect(new byte[0])).isEmpty();
        assertThat(extractor.detect(null)).isEmpty();
        // zip que não é docx
        assertThat(extractor.detect(new byte[] {'P', 'K', 3, 4, 0, 0})).isEmpty();
    }

    @Test
    void corruptFileIsUnprocessable() {
        assertThatThrownBy(() -> extractor.extract("%PDF-quebrado".getBytes(StandardCharsets.UTF_8), DocumentType.PDF))
                .isInstanceOf(DomainException.Unprocessable.class);
        assertThatThrownBy(() -> extractor.extract(new byte[] {1, 2, 3}, DocumentType.DOCX))
                .isInstanceOf(DomainException.Unprocessable.class);
    }

    private static byte[] pdf(String text) throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage();
            doc.addPage(page);
            try (var content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx(String text) throws Exception {
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }
}
