package com.fundcompass.matching.service;

import com.fundcompass.matching.domain.ConditionResult;
import com.fundcompass.matching.domain.ConditionVerdict;
import com.fundcompass.matching.domain.EligibilityVerdict;
import com.fundcompass.matching.dto.ExplanationResponse;
import com.fundcompass.matching.dto.ProgramMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.StringJoiner;

/**
 * F5 — 판정 결과를 사람이 읽는 문장으로 푼다.
 *
 * <p><b>판정을 다시 하지 않는다.</b> 기획서 원칙 —
 * *"LLM은 무엇을 추천할지 결정하지 않는다. 룰 엔진이 결정하고, LLM은 설명한다"* —
 * 이므로 이미 계산된 {@link ConditionResult}를 받아 서술만 한다. 프롬프트에 원본 공고문을
 * 넣지 않는 것도 같은 이유다. 원문을 주면 모델이 스스로 판정하려 들 여지가 생긴다.
 *
 * <p>목록이 아니라 <b>상세 조회 시점에만</b> 호출한다. 적격이 125건인데 전부 설명을 만들 이유가 없다.
 */
@Slf4j
@Service
public class ExplanationService {

    private static final String SYSTEM_PROMPT = """
            너는 정부 지원사업 적격 판정 결과를 소상공인에게 설명하는 도우미다.

            [절대 규칙 — 판정은 이미 끝났다]
            1. 너는 설명만 한다. 충족/미충족을 바꾸지 말 것.
            2. [충족한 조건]에 있는 항목은 이미 검증이 끝났다. 다시 의심하지 말 것.
               "확인이 필요합니다", "해당하는지 추가 확인" 같은 표현을 붙이면 안 된다.

               (X) 사업자 유형 충족  -> "소상공인이 중소기업에 해당하는지 추가 확인이 필요합니다"
               (O) 사업자 유형 충족  -> "사업자 유형이 지원 대상에 해당합니다"

            3. 주어진 정보에 없는 조건, 서류, 절차, 마감 여부를 지어내지 말 것.
            4. [미충족한 조건]이 하나도 없으면 탈락이라고 쓰지 말 것.

            [쓰는 법]
            - 2~4문장. 결론부터 말한다.
            - 존댓말. 전문용어를 풀어 쓴다.
            - 미충족이 있으면 무엇이 얼마나 모자란지 구체적으로 쓴다.
            - '신청 가능 시점'이 주어졌으면 그 날짜를 알려준다.
            - 마감이 '이미 마감됨'이면 신청할 수 없다고 분명히 알린다.
            - 마지막에 한 문장으로 공고문 원문 확인을 권한다.
              우리 판정이 놓친 조건이 있을 수 있다.
            """;

    private final ChatClient chatClient;

    /** 키가 없으면 빈이 없다. 그 경우 설명 없이 판정 결과만 쓴다 */
    public ExplanationService(@Autowired(required = false) @Nullable ChatClient explanationChatClient) {
        this.chatClient = explanationChatClient;
    }

    public ExplanationResponse explain(ProgramMatch match) {
        if (chatClient == null) {
            return ExplanationResponse.unavailable(match.programId(),
                    "설명 생성이 설정되지 않았습니다. 조건별 판정 결과를 참고하세요.");
        }
        try {
            String answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildPrompt(match))
                    .call()
                    .content();
            return ExplanationResponse.of(match.programId(), answer);
        } catch (Exception e) {
            // 설명이 실패해도 판정 결과는 유효하다. 화면 전체를 죽이지 않는다
            log.warn("설명 생성 실패 (programId={}): {}", match.programId(), e.getMessage());
            return ExplanationResponse.unavailable(match.programId(),
                    "설명을 생성하지 못했습니다. 조건별 판정 결과를 참고하세요.");
        }
    }

    /**
     * 프롬프트를 조립한다. <b>보내는 정보를 최소화한다</b> — 사용자의 사업자 정보가 나가는
     * 경로이므로, 판정에 실제로 쓰인 조건만 넣고 공고 원문과 프로필 원본은 넣지 않는다.
     * 매출도 구간 이름만 나간다(F13).
     */
    String buildPrompt(ProgramMatch match) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("[공고] ").append(match.name()).append('\n');
        prompt.append("[판정] ").append(verdictLabel(match.verdict())).append('\n');

        append(prompt, "충족한 조건", match, ConditionVerdict.SATISFIED);
        append(prompt, "미충족한 조건", match, ConditionVerdict.VIOLATED);

        if (!match.actionable().isEmpty()) {
            StringJoiner joiner = new StringJoiner(", ");
            match.actionable().forEach(c -> joiner.add(c.label()));
            prompt.append("[입력하면 더 정확해지는 항목] ").append(joiner).append('\n');
        }
        if (match.eligibleFrom() != null) {
            prompt.append("[신청 가능 시점] ").append(match.eligibleFrom()).append('\n');
        }
        // 숫자만 주면 "0일"을 마감된 것으로 읽는다. 실제로는 오늘까지 신청 가능하다.
        // 해석을 모델에 맡기지 않고 문장으로 준다
        if (match.applyEndDate() != null && match.daysLeft() != null) {
            // 음수를 그대로 주면 "-1일 남음"을 "하루 남았습니다"로 읽는다.
            // 0일만 처리하고 음수를 빠뜨려 실제로 마감된 공고를 "신청 가능"으로 안내했다
            String when = match.closed() ? " (이미 마감됨 — 신청할 수 없음)"
                    : match.daysLeft() == 0 ? " (오늘까지 신청 가능)"
                    : " (" + match.daysLeft() + "일 남음)";
            prompt.append("[신청 마감] ").append(match.applyEndDate()).append(when).append('\n');
        }
        return prompt.toString();
    }

    private void append(StringBuilder prompt, String label,
                        ProgramMatch match, ConditionVerdict verdict) {
        var items = match.conditions().stream().filter(c -> c.verdict() == verdict).toList();
        if (items.isEmpty()) {
            return;
        }
        prompt.append('[').append(label).append("]\n");
        for (ConditionResult c : items) {
            prompt.append("- ").append(c.label());
            if (c.requirement() != null) {
                prompt.append(" | 요구: ").append(c.requirement());
            }
            if (c.actual() != null) {
                prompt.append(" | 내 값: ").append(c.actual());
            }
            prompt.append('\n');
        }
    }

    /**
     * 판정을 모델이 오해하지 않도록 <b>뜻을 풀어서</b> 준다.
     *
     * <p>{@code NEEDS_REVIEW}를 "확인 필요"로만 주면 모델이 <b>충족으로 판정된 조건까지</b>
     * 의심해 "추가 확인이 필요합니다"를 붙였다. 실제 뜻은 "확인한 조건은 다 충족인데
     * 공고가 말하지 않은 조건이 있어 단정하지 못한다"이다.
     */
    private String verdictLabel(EligibilityVerdict verdict) {
        return switch (verdict) {
            case ELIGIBLE -> "신청 가능 (확인한 조건을 모두 충족)";
            case INELIGIBLE -> "신청 불가 (미충족 조건 있음)";
            case NEEDS_REVIEW ->
                    "확인한 조건은 모두 충족. 다만 공고가 명시하지 않은 조건이 있어 단정할 수 없음";
        };
    }
}
