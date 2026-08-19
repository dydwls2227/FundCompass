package com.fundcompass.program.service;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM이 인용한 evidence가 원문에 실제로 존재하는지 대조한다. 각색·조작 검출용.
 *
 * <p>모델은 근거를 여러 줄로 이어붙여 주는 경우가 많다. 그 조합은 원문에 그대로 존재하지 않으므로
 * 통째로 비교하면 멀쩡한 인용도 불일치로 잡힌다. 줄 단위로 쪼개 각각을 대조한다.
 *
 * <p><b>연속 일치만 요구하면 안 된다.</b> PDF 표는 열이 뒤엉켜 추출되므로, 모델이 표를 올바르게
 * 읽어도 그 문장은 원문에 연속으로 존재하지 않는다. 실측에서 이 오판이 정확한 독해 4건을
 * 환각으로 몰았다. 그래서 연속 일치에 실패한 줄은 한 번 더 조각으로 분해해 판정한다.
 */
public final class EvidenceVerifier {

    /** 이보다 짧은 줄은 우연히 일치할 수 있어 검증에서 제외한다 (공백 제거 후 기준) */
    private static final int MIN_LINE_LENGTH = 6;

    /**
     * 재배열로 인정할 최소 조각 길이. 이보다 짧은 조각이 끼면 글자를 바꿔 적은 것으로 본다.
     *
     * <p>실측 근거 (공백 제거 후):
     * <pre>
     *   한 글자 각색  "절약과" -> "절약 및"        조각 [7, 1, 21]   최소 1
     *   표 건너읽기   1462 매출/고용, 690 업력      최소 7 ~ 25
     * </pre>
     * 1과 7 사이가 비어 있어 5로 둔다. <b>각색 표본이 1건뿐이므로 경계는 잠정값이다.</b>
     * 바뀐 글자는 원문 어딘가에 있으니 조각으로는 남지만 이어지지 못해 짧게 고립된다.
     */
    private static final int MIN_RUN_LENGTH = 5;

    /**
     * @param checked    원문과 대조한 줄 수
     * @param matched    그중 원문에 연속으로 존재한 줄 수
     * @param reordered  연속은 아니지만 조각 전부가 충분히 긴 줄 수 (표를 건너뛰며 읽은 경우)
     * @param skipped    너무 짧아 대조를 건너뛴 줄 수. 근거는 줬으나 검증할 수 없다
     * @param altered    조각이 짧게 끊긴 줄. 글자를 바꿔 적은 것으로 본다
     */
    public record Result(int checked, int matched, int reordered, int skipped,
                         List<String> altered) {

        /** 모델이 근거를 주기는 했는가. 짧아서 검증 못 한 것과 아예 안 준 것은 다르다 */
        public boolean hasEvidence() {
            return checked > 0 || skipped > 0;
        }

        /** 모든 줄이 원문에 연속으로 존재한다 */
        public boolean allMatched() {
            return checked > 0 && altered.isEmpty() && reordered == 0;
        }

        /** 각색은 없다. 표를 건너뛰며 읽은 재배열은 포함한다 */
        public boolean faithful() {
            return checked > 0 && altered.isEmpty();
        }

        /** 근거는 있으나 전부 너무 짧아 판정을 보류해야 하는 상태 (예: "중소기업") */
        public boolean unverifiable() {
            return checked == 0 && skipped > 0;
        }
    }

    public static Result verify(String evidence, String source) {
        if (evidence == null || evidence.isBlank()) {
            return new Result(0, 0, 0, 0, List.of());
        }
        String haystack = squeeze(source);
        List<String> altered = new ArrayList<>();
        int checked = 0;
        int matched = 0;
        int reordered = 0;
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

            if (haystack.contains(squeezedLine)) {
                matched++;
                continue;
            }
            List<Integer> runs = runsOf(line, source);
            if (!runs.isEmpty() && runs.stream().allMatch(run -> run >= MIN_RUN_LENGTH)) {
                reordered++;                  // 표를 건너뛰며 읽었다. 조각은 전부 원문에 있다
            } else {
                altered.add(line.trim());
            }
        }
        return new Result(checked, matched, reordered, skipped, altered);
    }

    /**
     * 공백을 전부 제거한다. PDF 추출 텍스트는 단어 중간에 줄바꿈이나 공백이 끼는 경우가 많아
     * (예: {@code 예산 소진시까 지}) 공백을 살린 채로는 정확한 인용도 대조에 실패한다.
     */
    public static String squeeze(String text) {
        return text == null ? "" : text.replaceAll("\\s", "");
    }

    /**
     * 인용문을 원문에 존재하는 <b>최대 연속 구간</b>들로 쪼갠 길이 목록.
     *
     * <p>구분하려는 것은 이것이다.
     * <ul>
     *   <li>표를 건너뛰며 읽음 — 조각 하나하나가 <b>길다</b> (칸 단위로 끊긴다)</li>
     *   <li>글자를 바꿔 적음 — 바뀐 지점에서 조각이 <b>짧게</b> 끊긴다</li>
     * </ul>
     *
     * @return 각 조각의 길이. 원문에 아예 없는 글자를 만나면 빈 목록
     */
    public static List<Integer> runsOf(String line, String source) {
        String text = squeeze(line);
        String haystack = squeeze(source);
        List<Integer> runs = new ArrayList<>();

        int index = 0;
        while (index < text.length()) {
            int length = 0;
            while (index + length + 1 <= text.length()
                    && haystack.contains(text.substring(index, index + length + 1))) {
                length++;
            }
            if (length == 0) {
                return List.of();          // 원문에 없는 글자 -> 조각 분해 자체가 무의미
            }
            runs.add(length);
            index += length;
        }
        return runs;
    }

    private EvidenceVerifier() {
    }
}
