package com.fundcompass.matching.controller;

import com.fundcompass.matching.dto.ExplanationResponse;
import com.fundcompass.matching.dto.ProfileRequest;
import com.fundcompass.matching.dto.ProgramMatch;
import com.fundcompass.matching.service.ExplanationService;
import com.fundcompass.matching.service.MatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공고 상세 — 기획서 §4가 <b>"데모의 하이라이트"</b>로 꼽은 화면이다.
 * *"업력 2년 ✅ / 업종 일치 ✅ / 매출 3억 ❌ (기준 2억 이하)"*
 *
 * <p>설명 생성(F5)이 <b>목록이 아니라 여기에만</b> 붙는다. 적격이 125건인데 전부 설명을
 * 만들면 LLM 호출이 125배가 된다. 사용자가 실제로 열어본 것만 생성한다.
 */
@RestController
@RequestMapping("/api/matches/{programId}")
@RequiredArgsConstructor
public class MatchDetailController {

    private final MatchingService matchingService;
    private final ExplanationService explanationService;

    /** 조건별 체크리스트. LLM을 호출하지 않는다 */
    @PostMapping
    public ResponseEntity<ProgramMatch> detail(@PathVariable Long programId,
                                               @Valid @RequestBody ProfileRequest request) {
        ProgramMatch match = matchingService.matchOne(programId, request.toProfile());
        return match == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(match);
    }

    /**
     * 판정 결과를 자연어로 설명한다. <b>이 요청만 LLM을 호출한다.</b>
     *
     * <p>설명 생성이 꺼져 있거나 실패해도 200이다 —
     * {@code generated=false}로 알리고 화면은 조건별 체크리스트로 동작한다.
     */
    @PostMapping("/explanation")
    public ResponseEntity<ExplanationResponse> explanation(@PathVariable Long programId,
                                                           @Valid @RequestBody ProfileRequest request) {
        ProgramMatch match = matchingService.matchOne(programId, request.toProfile());
        return match == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(explanationService.explain(match));
    }
}
