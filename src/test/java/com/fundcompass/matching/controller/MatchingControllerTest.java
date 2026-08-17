package com.fundcompass.matching.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F3 → F4 엔드포인트를 실제 HTTP로 검사한다. LLM 호출 0회, DB의 1,447건을 그대로 쓴다.
 *
 * <p>컨트롤러가 이 프로젝트의 첫 API 표면이라 직렬화·검증이 실제로 도는지 확인이 필요하다.
 * 특히 {@code ConditionResult}의 record 중첩과 enum 직렬화는 컴파일만으로는 알 수 없다.
 */
@SpringBootTest
class MatchingControllerTest {

    @Autowired WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static final String 소상공인 = """
            {
              "foundedOn": "2024-03-01",
              "revenueBand": "UNDER_3",
              "employeeCount": 3,
              "province": "경상북도",
              "city": "포항시",
              "businessType": "소상공인"
            }
            """;

    @Test
    @DisplayName("적격 공고를 마감 임박순으로 내려준다")
    void 적격_조회() throws Exception {
        MvcResult result = mockMvc().perform(post("/api/matches")
                        .param("verdict", "ELIGIBLE")
                        .param("limit", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(소상공인))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("ELIGIBLE"))
                .andExpect(jsonPath("$.counts.ELIGIBLE").exists())
                .andExpect(jsonPath("$.items[0].name").exists())
                .andExpect(jsonPath("$.items[0].conditions").isArray())
                .andReturn();

        System.out.println("=".repeat(78));
        System.out.println(result.getResponse().getContentAsString()
                .replaceAll("(\\{\"programId)", "\n  $1"));
        System.out.println("=".repeat(78));
    }

    @Test
    @DisplayName("빈 프로필도 200이다 — 비로그인 부분 입력 허용 (F11)")
    void 빈_프로필() throws Exception {
        MvcResult result = mockMvc().perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        System.out.println("빈 프로필 집계: " + result.getResponse().getContentAsString()
                .replaceAll("\"items\".*", "\"items\": [...]}"));
    }

    @Test
    @DisplayName("미래 창업일은 400으로 거른다")
    void 잘못된_창업일() throws Exception {
        mockMvc().perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foundedOn\":\"2099-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("limit은 상한으로 잘린다")
    void limit_상한() throws Exception {
        MvcResult result = mockMvc().perform(post("/api/matches")
                        .param("limit", "9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(소상공인))
                .andExpect(status().isOk())
                .andReturn();

        int items = result.getResponse().getContentAsString().split("\"programId\"", -1).length - 1;
        assertThat(items).isLessThanOrEqualTo(100);
        System.out.printf("limit=9999 요청 -> %d건 반환 (상한 100)%n", items);
    }

    @Test
    @DisplayName("응답 시간 측정 — 요청마다 1,447건을 판정한다")
    void 응답_시간() throws Exception {
        long total = 0;
        int runs = 5;
        for (int i = 0; i < runs; i++) {
            long started = System.currentTimeMillis();
            mockMvc().perform(post("/api/matches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(소상공인))
                    .andExpect(status().isOk());
            total += System.currentTimeMillis() - started;
        }
        System.out.printf("평균 응답 %,dms (%d회)%n", total / runs, runs);
    }
}
