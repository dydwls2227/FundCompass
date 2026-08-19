package com.fundcompass.matching.service;

import com.fundcompass.matching.domain.BusinessProfile;
import com.fundcompass.matching.domain.EligibilityVerdict;
import com.fundcompass.matching.domain.MatchResult;
import com.fundcompass.matching.dto.MatchResponse;
import com.fundcompass.matching.dto.ProgramMatch;
import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.domain.Program;
import com.fundcompass.program.domain.ProgramEligibility;
import com.fundcompass.program.repository.ProgramEligibilityRepository;
import com.fundcompass.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 프로필 하나를 전체 공고에 대고 판정한다. F4의 API 진입점.
 *
 * <p>자격요건이 추출된 공고를 <b>전부 메모리에 올려 자바로 판정한다.</b> SQL 필터링을 쓰지 않는
 * 이유는 조건이 JSONB에 들어 있어서가 아니라, 판정 로직이 코드로 추적 가능해야 하기 때문이다
 * (기획서: *"판정 근거가 코드로 추적·재현 가능하다"*). 1,447건 규모에서는 비용도 문제되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final ProgramRepository programRepository;
    private final ProgramEligibilityRepository eligibilityRepository;
    private final EligibilityMatcher matcher;

    /**
     * @param verdict 이 판정만 목록으로 받는다. {@code null}이면 적격 -> 확인필요 -> 부적격 순 전체
     * @param limit   목록 최대 건수. 집계({@code counts})는 이 값과 무관하게 전체를 센다
     */
    @Transactional(readOnly = true)
    public MatchResponse match(BusinessProfile profile, EligibilityVerdict verdict, int limit) {
        LocalDate today = LocalDate.now();
        long started = System.currentTimeMillis();

        List<ProgramEligibility> rows = eligibilityRepository.findAll().stream()
                .filter(r -> r.getStatus() == ExtractionStatus.EXTRACTED)
                .filter(r -> r.getExtraction() != null)
                .toList();

        Map<Long, Program> programs = programRepository
                .findAllById(rows.stream().map(ProgramEligibility::getProgramId).toList())
                .stream()
                .collect(Collectors.toMap(Program::getId, Function.identity()));

        List<ProgramMatch> matches = new ArrayList<>();
        int excludedClosed = 0;

        for (ProgramEligibility row : rows) {
            Program program = programs.get(row.getProgramId());
            if (program == null) {
                continue;
            }
            // 이미 마감된 공고는 판정해도 쓸 데가 없다. 기한이 없는 공고(864건)는
            // 상시모집일 수 있으므로 제외하지 않는다 — 확실히 지난 것만 뺀다
            if (program.getApplyEndDate() != null && program.getApplyEndDate().isBefore(today)) {
                excludedClosed++;
                continue;
            }
            MatchResult result = matcher.match(row, profile, today);
            matches.add(ProgramMatch.of(program, result, today));
        }

        Map<EligibilityVerdict, Integer> counts = new EnumMap<>(EligibilityVerdict.class);
        for (EligibilityVerdict v : EligibilityVerdict.values()) {
            counts.put(v, 0);
        }
        matches.forEach(m -> counts.merge(m.verdict(), 1, Integer::sum));

        List<ProgramMatch> items = matches.stream()
                .filter(m -> verdict == null || m.verdict() == verdict)
                .sorted(ordering())
                .limit(limit)
                .toList();

        log.debug("매칭 완료 - 공고 {}건, 마감 제외 {}건, {}ms",
                matches.size(), excludedClosed, System.currentTimeMillis() - started);

        return new MatchResponse(counts, verdict, items, matches.size(), excludedClosed);
    }

    /**
     * 공고 하나만 판정한다. 상세 조회(F5 설명 생성)용.
     *
     * @return 자격요건이 추출되지 않은 공고면 {@code null}
     */
    @Transactional(readOnly = true)
    public ProgramMatch matchOne(Long programId, BusinessProfile profile) {
        LocalDate today = LocalDate.now();
        ProgramEligibility row = eligibilityRepository.findByProgramId(programId)
                .filter(r -> r.getStatus() == ExtractionStatus.EXTRACTED)
                .filter(r -> r.getExtraction() != null)
                .orElse(null);
        if (row == null) {
            return null;
        }
        Program program = programRepository.findById(programId).orElse(null);
        if (program == null) {
            return null;
        }
        return ProgramMatch.of(program, matcher.match(row, profile, today), today);
    }

    /**
     * 적격 우선, 그다음 마감 임박순.
     *
     * <p>부적격이 60%를 넘으므로(지자체 사업이 많아 지역 조건에서 걸린다) 판정 순서가 없으면
     * 사용자가 쓸 수 있는 공고가 목록 아래에 묻힌다.
     *
     * <p>기한이 없는 공고는 뒤로 보낸다 — 마감이 임박한 공고가 더 급하다.
     * 같은 조건이면 판정에 쓰인 조건이 많은 쪽(근거가 충실한 쪽)을 앞세운다.
     */
    private Comparator<ProgramMatch> ordering() {
        return Comparator
                .comparingInt((ProgramMatch m) -> switch (m.verdict()) {
                    case ELIGIBLE -> 0;
                    case NEEDS_REVIEW -> 1;
                    case INELIGIBLE -> 2;
                })
                .thenComparing(ProgramMatch::daysLeft,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Comparator.comparingInt(ProgramMatch::judgedCount).reversed());
    }
}
