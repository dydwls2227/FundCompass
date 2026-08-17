package com.fundcompass.program.infra;

/**
 * 추출된 문서 텍스트를 저장·프롬프트에 넣기 전 상태로 다듬는다.
 *
 * <p>PDF와 HWP가 같은 규칙을 써야 한다. 특히 제어문자 제거는 선택이 아니다 —
 * PostgreSQL {@code text}는 NUL(0x00)을 저장할 수 없어 배치가 통째로 죽는다
 * (2-1에서 실제로 겪었다). hwplib도 문단 사이에 제어문자를 섞는다.
 */
public final class DocumentText {

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private DocumentText() {
    }
}
