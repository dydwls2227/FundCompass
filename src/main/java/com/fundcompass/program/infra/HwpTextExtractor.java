package com.fundcompass.program.infra;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * HWP 계열 바이트에서 텍스트 추출. {@code .hwp}와 {@code .hwpx}는 포맷이 전혀 다르다.
 *
 * <ul>
 *   <li>{@code .hwp} — CFBF 컨테이너 + zlib 압축 레코드. hwplib에 맡긴다</li>
 *   <li>{@code .hwpx} — ZIP + OWPML XML. JDK만으로 읽는다
 *       (hwpxlib 1.0.5에는 텍스트 추출기가 없고 파일 경로만 받는다)</li>
 * </ul>
 *
 * <p><b>확장자가 아니라 매직 바이트로 분기한다.</b> 기업마당 첨부는 확장자와 실제 내용이
 * 어긋나는 경우가 있다 (2-2에서 {@code .pdf}인데 스캔 이미지인 문서를 146건 발견했다).
 */
@Component
public class HwpTextExtractor {

    public String extract(byte[] bytes) throws Exception {
        if (isCompoundFile(bytes)) {
            return DocumentText.normalize(readHwp(bytes));
        }
        if (isZip(bytes)) {
            return DocumentText.normalize(readHwpx(bytes));
        }
        // 무엇인지 모르는 채 빈 문자열을 반환하면 markExtracted("")로 성공 기록된다.
        // 2-2에서 그 방식이 "EXTRACTED인데 텍스트가 빈 문서 146건"을 만들었다
        throw new IllegalArgumentException("HWP/HWPX 형식이 아님 - 매직 " + magicOf(bytes));
    }

    private String readHwp(byte[] bytes) throws Exception {
        HWPFile file = HWPReader.fromInputStream(new ByteArrayInputStream(bytes));
        return TextExtractor.extract(file, TextExtractMethod.InsertControlTextBetweenParagraphText);
    }

    /** 본문은 {@code Contents/section*.xml}의 {@code <hp:t>} 요소다 */
    private String readHwpx(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!isSectionXml(entry.getName())) {
                    continue;
                }
                // XMLStreamReader가 다 읽고 나서 밑의 ZipInputStream을 닫아버린다.
                // 엔트리를 바이트로 떠서 별도 스트림으로 파싱해야 다음 엔트리를 읽을 수 있다
                appendSection(text, factory, zip.readAllBytes());
            }
        }
        return text.toString();
    }

    private void appendSection(StringBuilder text, XMLInputFactory factory, byte[] xml)
            throws Exception {
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
        boolean inText = false;
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT ->
                        inText = "t".equals(reader.getLocalName());
                case XMLStreamConstants.CHARACTERS -> {
                    if (inText) {
                        text.append(reader.getText());
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("t".equals(reader.getLocalName())) {
                        inText = false;
                    } else if ("p".equals(reader.getLocalName())) {
                        text.append('\n');          // 문단 경계 보존
                    }
                }
                default -> { }
            }
        }
        reader.close();
    }

    private boolean isSectionXml(String name) {
        return name.startsWith("Contents/section") && name.endsWith(".xml");
    }

    /** CFBF(Compound File Binary Format) 서명 D0 CF 11 E0 */
    private boolean isCompoundFile(byte[] bytes) {
        return bytes.length >= 4
                && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF
                && (bytes[2] & 0xFF) == 0x11 && (bytes[3] & 0xFF) == 0xE0;
    }

    private boolean isZip(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private String magicOf(byte[] bytes) {
        if (bytes.length < 4) {
            return "길이 " + bytes.length;
        }
        return String.format("%02X %02X %02X %02X",
                bytes[0], bytes[1], bytes[2], bytes[3]);
    }
}
