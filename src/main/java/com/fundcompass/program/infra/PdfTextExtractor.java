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
            return normalize(stripper.getText(document));
        }
    }

    // 공백,줄바꿈을 정리하여 LLM 입력 토큰 줄임
    private String normalize(String raw) {
        if(raw==null){
            return "";
        }
        return raw.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

    }
}
