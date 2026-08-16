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

    /**
     * @param checked 실제로 원문과 대조한 줄 수
     * @param matched 그중 일치한 줄 수
     * @param skipped 너무 짧아 대조를 건너뛴 줄 수. 모델은 근거를 줬지만 검증할 수 없는 경우다
     */
    public record Result(int checked, int matched, int skipped, List<String> missing) {

        /** 모델이 근거를 주기는 했는가. 짧아서 검증 못 한 것과 아예 안 준 것은 다르다 */
        public boolean hasEvidence() {
            return checked > 0 || skipped > 0;
        }

        public boolean allMatched() {
            return checked > 0 && missing.isEmpty();
        }

        /** 근거는 있으나 전부 너무 짧아 판정을 보류해야 하는 상태 (예: "중소기업") */
        public boolean unverifiable() {
            return checked == 0 && skipped > 0;
        }
    }

    public static Result verify(String evidence, String source) {
        if (evidence == null || evidence.isBlank()) {
            return new Result(0, 0, 0, List.of());
        }
        String squeezedSource = squeeze(source);
        List<String> missing = new ArrayList<>();
        int checked = 0;
        int matched = 0;
        int skipped = 0;

        for (String line : evidence.split("\\R")) {
            String squeezedLine = squeeze(line);
            if (squeezedLine.isEmpty()) {
                continue;
            }
            if (squeezedLine.length() < MIN_LINE_LENGTH) {
                skipped++;                    // 근거를 안 준 것이 아니라 검증할 수 없는 것이다
                continue;
            }
            checked++;
            if (squeezedSource.contains(squeezedLine)) {
                matched++;
            } else {
                missing.add(line.trim());
            }
        }
        return new Result(checked, matched, skipped, missing);
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
