package com.fundcompass.matching.config;

import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * F5 설명 생성용 {@link ChatClient}를 <b>별도로</b>만든다.
 *
 * <p>Spring AI 자동설정이 만드는 {@code ChatClient.Builder}는 {@code spring.ai.google.genai}의
 * 키를 쓰며 공고 추출(F2)이 사용한다. 여기서는 그것을 재사용하지 않고
 * {@code fundcompass.explanation}의 별도 키로 모델을 직접 조립한다 —
 * 사용자 데이터를 보내는 경로와 공개 문서를 보내는 경로를 키 수준에서 분리하기 위해서다.
 *
 * <p>키가 없으면 빈을 만들지 않는다. F5는 그 경우 설명 없이 판정 결과만 반환하므로
 * <b>키 없이도 서비스가 동작한다</b> — 저장소를 받아 바로 띄워보는 사람이 막히지 않는다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ExplanationProperties.class)
public class ExplanationConfig {

    @Bean
    public ChatClient explanationChatClient(ExplanationProperties properties) {
        if (!properties.enabled()) {
            log.info("F5 설명 생성 비활성 - fundcompass.explanation.api-key 미설정");
            return null;
        }
        Client genAiClient = Client.builder()
                .apiKey(properties.apiKey())
                .vertexAI(false)          // 명시하지 않으면 GCP 과금 경로로 넘어갈 수 있다
                .build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .genAiClient(genAiClient)
                .options(GoogleGenAiChatOptions.builder()
                        .model(properties.model())
                        .temperature(0.3)          // 설명은 약간의 자연스러움이 필요하다
                        .maxOutputTokens(properties.maxOutputTokens())
                        .thinkingBudget(0)         // 설명에 추론이 필요 없다. 13배 빠르다
                        .build())
                .build();

        log.info("F5 설명 생성 활성 - model={}", properties.model());
        return ChatClient.builder(model).build();
    }
}
