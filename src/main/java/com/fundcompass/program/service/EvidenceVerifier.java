package com.fundcompass.program.service;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM이 인용한 evidence가 원문에 실제로 존재하는지 대조한다. 환각 검출용.
 *
 * <p>모델은 근거를 여러 줄로 이어붙여 주는 경우가 많다. 그 조합은 원문에 그대로 존재하지 않으므로
 * 통째로 비교하면 멀쩡한 인용도 불일치로 잡힌다. 줄 단위로 쪼개 각각을 대조한다.
 */
public final class EvidenceVerifier {

    /** 이보다 짧은 줄은 우연히 일치할 수 있어 검증에서 제외한다 (공백 제거 후 기준) */
    private static final int MIN_LINE_LENGTH = 6;

    public record Result(int checked, int matched, List<String> missing) {

        public boolean hasEvidence() {
            return checked > 0;
        }

        public boolean allMatched() {
            return checked > 0 && missing.isEmpty();
        }
    }

    public static Result verify(String evidence, String source) {
        if (evidence == null || evidence.isBlank()) {
            return new Result(0, 0, List.of());
        }
        String squeezedSource = squeeze(source);
        List<String> missing = new ArrayList<>();
        int checked = 0;
        int matched = 0;

        for (String line : evidence.split("\\R")) {
            String squeezedLine = squeeze(line);
            if (squeezedLine.length() < MIN_LINE_LENGTH) {
                continue;
            }
            checked++;
            if (squeezedSource.contains(squeezedLine)) {
                matched++;
            } else {
                missing.add(line.trim());
            }
        }
        return new Result(checked, matched, missing);
    }

    /**
     * 공백을 전부 제거한다. PDF 추출 텍스트는 단어 중간에 줄바꿈이나 공백이 끼는 경우가 많아
     * (예: {@code 예산 소진시까 지}) 공백을 살린 채로는 정확한 인용도 대조에 실패한다.
     */
    public static String squeeze(String text) {
        return text == null ? "" : text.replaceAll("\\s", "");
    }

    private EvidenceVerifier() {
    }
}
