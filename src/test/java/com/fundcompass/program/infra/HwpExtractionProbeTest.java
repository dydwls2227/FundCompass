package com.fundcompass.program.infra;

import com.fundcompass.program.domain.DocumentFileType;
import com.fundcompass.program.domain.ProgramDocument;
import com.fundcompass.program.repository.ProgramDocumentRepository;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * HWP·HWPX 텍스트 추출이 실제 공고 문서에서 쓸 만한지 재본다. LLM 호출 0회.
 *
 * <p>2-1에서 PDF를 검증한 것과 같은 기준을 쓴다 — 깨짐 문자 수, 한글 비율, 길이.
 * 라이브러리가 예외 없이 열리는 것과 <b>한글이 제대로 나오는 것</b>은 다른 문제다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HwpExtractionProbeTest {

    /** 각 확장자별로 몇 건씩 시험할지 */
    private static final int SAMPLE = 5;

    @Autowired ProgramDocumentRepository documentRepository;
    @Autowired FileDownloader fileDownloader;

    @Test
    @DisplayName("HWP·HWPX 추출 품질 실측 (LLM 호출 없음)")
    void 추출_품질() {
        List<ProgramDocument> all = documentRepository.findAll().stream()
                .filter(d -> d.getFileType() == DocumentFileType.HWP)
                .filter(d -> d.getFileUrl() != null)
                .toList();

        System.out.println("=".repeat(96));
        probe("hwpx", all.stream()
                .filter(d -> d.getFileName().toLowerCase().endsWith(".hwpx"))
                .limit(SAMPLE).toList());
        System.out.println("-".repeat(96));
        probe("hwp", all.stream()
                .filter(d -> d.getFileName().toLowerCase().endsWith(".hwp"))
                .limit(SAMPLE).toList());
        System.out.println("=".repeat(96));
    }

    private void probe(String label, List<ProgramDocument> documents) {
        System.out.printf("[%s] %d건%n", label, documents.size());

        for (ProgramDocument document : documents) {
            try {
                byte[] bytes = fileDownloader.download(document.getFileUrl());
                String magic = magicOf(bytes);
                String text = "hwpx".equals(label) ? readHwpx(bytes) : readHwp(bytes);

                long korean = text.chars().filter(c -> c >= 0xAC00 && c <= 0xD7A3).count();
                long broken = text.chars().filter(c -> c == 0xFFFD || c == 0).count();

                System.out.printf("  ✅ [%5d] %-34s  매직 %s  %,7d자  한글 %2d%%  깨짐 %d%n",
                        document.getId(), trim(document.getFileName()), magic,
                        text.length(),
                        text.isEmpty() ? 0 : 100 * korean / text.length(), broken);
                System.out.printf("        %s%n", preview(text));
            } catch (Exception e) {
                System.out.printf("  🚨 [%5d] %-34s  %s: %s%n",
                        document.getId(), trim(document.getFileName()),
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private String readHwp(byte[] bytes) throws Exception {
        HWPFile file = HWPReader.fromInputStream(new ByteArrayInputStream(bytes));
        return TextExtractor.extract(file, TextExtractMethod.InsertControlTextBetweenParagraphText);
    }

    /**
     * hwpx는 ZIP 안에 OWPML XML이 들어 있다. 본문은 {@code Contents/section*.xml}의
     * {@code <hp:t>} 요소다. 정규식이 아니라 StAX로 읽어 XML 엔티티를 정상 처리한다.
     */
    private String readHwpx(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith("Contents/section") || !name.endsWith(".xml")) {
                    continue;
                }
                // XMLStreamReader가 다 읽고 나서 밑의 ZipInputStream을 닫아버린다.
                // 엔트리를 바이트로 떠서 별도 스트림으로 파싱해야 다음 엔트리를 읽을 수 있다
                byte[] entryBytes = zip.readAllBytes();
                XMLStreamReader reader =
                        factory.createXMLStreamReader(new ByteArrayInputStream(entryBytes));
                boolean inText = false;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        inText = "t".equals(reader.getLocalName());
                    } else if (event == XMLStreamConstants.CHARACTERS && inText) {
                        text.append(reader.getText());
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if ("t".equals(reader.getLocalName())) {
                            inText = false;
                        } else if ("p".equals(reader.getLocalName())) {
                            text.append('\n');      // 문단 경계 보존
                        }
                    }
                }
            }
        }
        return text.toString();
    }

    /** 확장자를 믿지 않는다. .hwp 인데 ZIP(=hwpx)인 경우가 실제로 있다 */
    private String magicOf(byte[] bytes) {
        if (bytes.length < 4) {
            return "??";
        }
        if ((bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF) {
            return "CFBF";
        }
        if (bytes[0] == 'P' && bytes[1] == 'K') {
            return "ZIP ";
        }
        if (bytes[0] == '%' && bytes[1] == 'P') {
            return "PDF ";
        }
        return String.format("%02X%02X", bytes[0], bytes[1]);
    }

    private String trim(String name) {
        return name == null ? "-" : name.length() <= 34 ? name : name.substring(0, 31) + "...";
    }

    private String preview(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 80 ? flat : flat.substring(0, 80) + "…";
    }
}
