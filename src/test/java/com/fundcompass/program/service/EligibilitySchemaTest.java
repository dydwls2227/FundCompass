package com.fundcompass.program.service;

import com.fundcompass.program.infra.dto.EligibilityExtraction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모델이 실제로 읽는 스키마 텍스트를 검사한다. Spring 컨텍스트도 API 호출도 필요 없다.
 *
 * <p>스키마는 프롬프트에 텍스트로 삽입되므로 {@code @JsonPropertyDescription}이 곧 지시문이다.
 * DTO를 고치면서 이 텍스트가 조용히 바뀌거나 사라지는 것을 막는다.
 */
class EligibilitySchemaTest {

    private final String schema =
            new BeanOutputConverter<>(EligibilityExtraction.class).getJsonSchema();

    @Test
    @DisplayName("evidence 지시가 스키마에 실린다")
    void evidence_지시_확인() {
        System.out.println(schema);

        assertThat(schema)
                .as("evidence를 '답의 재진술'이 아니라 '인용'으로 규정하는 문구")
                .contains("읽어낸 자리를 가리키는 칸");
    }

    /**
     * victools는 재사용 타입(RangeCondition 3회, ListCondition 3회)을 {@code $ref}로 뽑는다.
     *
     * <p>이 자체는 문제가 아니다. {@code useProviderStructuredOutput()}을 쓰던 시절에는
     * Gemini가 {@code responseSchema}의 {@code $ref}를 해석하지 못해 필드가 빈 객체로 보였지만,
     * 지금은 스키마를 프롬프트 텍스트로 넣으므로 모델이 같은 문서 안의 정의를 읽어 해결한다.
     *
     * <p>따라서 지켜야 할 것은 "$ref가 없을 것"이 아니라 <b>"참조 대상이 같은 문서 안에 있을 것"</b>이다.
     * 정의가 빠지면 모델이 그 필드의 모양을 알 수 없다.
     */
    @Test
    @DisplayName("모든 $ref의 정의가 같은 문서 안에 있다")
    void 참조_대상_존재() {
        Matcher matcher = Pattern.compile("\"\\$ref\"\\s*:\\s*\"#/\\$defs/(\\w+)\"").matcher(schema);

        Set<String> referenced = new LinkedHashSet<>();
        while (matcher.find()) {
            referenced.add(matcher.group(1));
        }

        assertThat(referenced)
                .as("재사용 타입이 참조로 뽑히는 현재 동작")
                .isNotEmpty();

        for (String name : referenced) {
            assertThat(schema)
                    .as("$defs 안의 %s 정의", name)
                    .contains("\"" + name + "\" : {");
        }
    }
}
