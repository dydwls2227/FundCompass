package com.fundcompass.matching.controller;

import com.fundcompass.matching.domain.EligibilityVerdict;
import com.fundcompass.matching.dto.MatchResponse;
import com.fundcompass.matching.dto.ProfileRequest;
import com.fundcompass.matching.service.MatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * F3 프로필 입력 → F4 판정 결과.
 *
 * <p><b>{@code POST}인데 상태를 만들지 않는다.</b> 프로필이 개인정보라 쿼리스트링에 실을 수 없어
 * 본문으로 받는 것이고, 서버는 판정만 하고 버린다 — 세션에도 DB에도 저장하지 않는다.
 * 기획서 F13의 *"비로그인 데이터는 서버 영구 저장 없이 세션 범위로 한정"* 보다 한 단계 더
 * 보수적인 선택이다. 로그인 사용자의 프로필 저장은 F12에서 별도로 다룬다.
 */
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchingController {

    /** 한 번에 내려보낼 최대 건수. 부적격이 900건을 넘으므로 상한이 필요하다 */
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 20;

    private final MatchingService matchingService;

    /**
     * @param verdict {@code ELIGIBLE} / {@code NEEDS_REVIEW} / {@code INELIGIBLE}.
     *                생략하면 적격 우선 순서로 전체에서 가져온다
     */
    @PostMapping
    public MatchResponse match(@Valid @RequestBody ProfileRequest request,
                               @RequestParam(required = false) EligibilityVerdict verdict,
                               @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return matchingService.match(
                request.toProfile(), verdict, Math.clamp(limit, 1, MAX_LIMIT));
    }
}
