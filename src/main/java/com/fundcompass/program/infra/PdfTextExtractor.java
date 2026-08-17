package com.fundcompass.program.infra;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

// PDF 바이트에서 텍스트 추출
@Component
public class PdfTextExtractor {

    public String extract(byte[] pdfBytes) throws IOException {
        try(PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return DocumentText.normalize(stripper.getText(document));
        }
    }

}
