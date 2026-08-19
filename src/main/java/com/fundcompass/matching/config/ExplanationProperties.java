package com.fundcompass.matching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * F5 설명 생성 전용 LLM 설정. <b>공고 추출용 키와 의도적으로 분리한다.</b>
 *
 * <p>F5는 사용자의 사업자 정보(매출 구간·고용인원·업력·지역)를 LLM에 보낸다. 공고 추출은
 * 기업마당 공개 문서만 보내므로 성격이 전혀 다르다. 무료 티어는 입력이 제품 개선에 사용되므로
 * <b>사용자 데이터를 다루는 이쪽만 유료 키를 쓴다</b> — 추출은 필요하면 무료로 되돌릴 수 있다.
 *
 * <p>키를 분리하면 사용량·비용도 따로 보이고, 한쪽이 쿼터에 막혀도 다른 쪽은 계속 돈다.
 *
 * @param apiKey         F5 전용 API 키. 비어 있으면 설명 생성을 건너뛴다(판정 결과는 그대로 반환)
 * @param model          모델 ID
 * @param maxOutputTokens 설명은 짧아야 하므로 낮게 잡는다
 */
@ConfigurationProperties(prefix = "fundcompass.explanation")
public record ExplanationProperties(
        String apiKey,
        String model,
        Integer maxOutputTokens
) {
    public ExplanationProperties {
        model = (model == null || model.isBlank()) ? "gemini-2.5-flash" : model;
        maxOutputTokens = maxOutputTokens == null ? 1024 : maxOutputTokens;
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
