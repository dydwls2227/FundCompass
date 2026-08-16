package com.fundcompass.program.service;

import com.fundcompass.program.domain.ExtractionStatus;
import com.fundcompass.program.domain.Program;
import com.fundcompass.program.domain.ProgramDocument;
import com.fundcompass.program.domain.ProgramEligibility;
import com.fundcompass.program.infra.dto.EligibilityExtraction;
import com.fundcompass.program.repository.ProgramDocumentRepository;
import com.fundcompass.program.repository.ProgramEligibilityRepository;
import com.fundcompass.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EligibilityBatchService {

    static final String PROMPT_VERSION = "v3";
    private static final long CALL_INTERVAL_MS = 6_500;   // RPM 10 -> 6초 + 여유

    // 이 횟수까지 실패하면 데이터 문제로 보고 포기한다. 그 전까지는 일시적 오류일 수 있어 다시 도전한다
    private static final int MAX_ATTEMPTS = 3;

    // 정량 조건이 담긴 표현. 많을수록 먼저 처리한다 (하루 20건 제약 아래서 가치 순으로 쌓기 위해)
    private static final Pattern CONDITION_SIGNAL = Pattern.compile(
            "(업력|매출|근로자|고용|종업원|창업)[^\\n]{0,25}?"
                    + "\\d+\\s?(년|개월|억|천만|명|인)\\s?원?\\s?(이내|이상|미만|이하)");

    private final ProgramRepository programRepository;
    private final ProgramDocumentRepository documentRepository;
    private final ProgramEligibilityRepository eligibilityRepository;
    private final EligibilityExtractor extractor;
    private final EligibilitySectionExtractor sectionExtractor;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.google.genai.chat.model}")
    private String modelId;

    public record BatchResult(int extracted, int failed, int remaining, boolean quotaExceeded) {}


    public BatchResult extractPending(int limit) {
        List<Long> targets = findTargets();
        int total = targets.size();
        int extracted = 0, failed = 0;
        boolean quotaExceeded = false;

        for (Long programId : targets.stream().limit(limit).toList()) {
            Program program = programRepository.findById(programId).orElse(null);
            if (program == null) {
                continue;
            }
            List<ProgramDocument> documents = documentRepository
                    .findByProgramIdAndStatus(programId, ExtractionStatus.EXTRACTED);
            // 모델에 보낸 것과 같은 문자열이라야 evidence 대조가 성립한다
            String source = extractor.buildPrompt(program, documents);
            int promptChars = source.length();

            long started = System.currentTimeMillis();
            try {
                EligibilityExtraction result = extractor.extract(program, documents);
                String json = objectMapper.writeValueAsString(result);
                long elapsed = System.currentTimeMillis() - started;

                List<String> unverified =
                        EligibilityVerification.unmatchedFields(result, source);
                if (!unverified.isEmpty()) {
                    log.warn("근거 미확인 - F4 판정에서 제외 (programId={}): {}", programId, unverified);
                }

                // 이전 실패로 행이 남아 있을 수 있다. 공고당 1행이라 insert하면 유니크 제약 위반이다
                eligibilityRepository.save(eligibilityRepository.findByProgramId(programId)
                        .map(row -> row.markSucceeded(
                                json, PROMPT_VERSION, modelId, promptChars, elapsed, unverified))
                        .orElseGet(() -> ProgramEligibility.succeeded(
                                programId, json, PROMPT_VERSION, modelId, promptChars,
                                elapsed, unverified)));
                extracted++;
            } catch (Exception e) {
                String quotaCause = quotaCauseOf(e);
                if (quotaCause != null) {
                    // 최상위 메시지는 "Failed to generate content" 처럼 일반적이라 판단 근거가 남지 않는다.
                    // 어느 원인이 쿼터로 판정됐는지 남겨야 오분류(진짜 429가 아닌데 중단)를 나중에 가릴 수 있다
                    log.warn("쿼터 소진 - 배치 중단 (programId={}): {} | 판정 근거: {}",
                            programId, e.getMessage(), quotaCause);
                    quotaExceeded = true;
                    break;                    // 행을 만들지 않는다 -> 다음 실행에서 이어짐
                }
                failed++;
                log.warn("추출 실패 (programId={}): {}", programId, e.getMessage());
                try {
                    eligibilityRepository.save(eligibilityRepository.findByProgramId(programId)
                            .map(row -> row.markFailed(
                                    e.getMessage(), PROMPT_VERSION, modelId, promptChars))
                            .orElseGet(() -> ProgramEligibility.failed(
                                    programId, e.getMessage(), PROMPT_VERSION, modelId, promptChars)));
                } catch (Exception ignored) {
                }
            }
            sleep();
        }

        BatchResult result = new BatchResult(
                extracted, failed, total - extracted - failed, quotaExceeded);
        log.info("자격요건 추출 완료 - 성공 {}, 실패 {}, 남음 {}, 쿼터소진 {}",
                result.extracted(), result.failed(), result.remaining(), result.quotaExceeded());
        return result;
    }

    List<Long> findTargets() {
        Set<Long> done = new HashSet<>(eligibilityRepository.findSettledProgramIds(
                ExtractionStatus.EXTRACTED, MAX_ATTEMPTS));

        Map<Long, List<ProgramDocument>> byProgram = documentRepository
                .findByStatus(ExtractionStatus.EXTRACTED).stream()
                .filter(document -> document.getExtractedText() != null
                        && !document.getExtractedText().isBlank())
                .filter(document -> !done.contains(document.getProgramId()))
                .collect(Collectors.groupingBy(ProgramDocument::getProgramId));

        return byProgram.entrySet().stream()
                .sorted(Comparator.comparingInt(
                        (Map.Entry<Long, List<ProgramDocument>> entry)
                                -> conditionSignals(entry.getValue())).reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    // 실제로 프롬프트에 실릴 구간에서만 센다. 잘려나갈 조건은 우선순위를 줄 이유가 없다
    private int conditionSignals(List<ProgramDocument> documents) {
        int count = 0;
        for (ProgramDocument document : documents) {
            Matcher matcher = CONDITION_SIGNAL.matcher(
                    sectionExtractor.extract(document.getExtractedText()));
            while (matcher.find()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 쿼터 초과로 판정되면 그 근거가 된 원인 문자열을, 아니면 {@code null}을 반환한다.
     *
     * <p>예외 타입이 아니라 메시지로 판정하는 것은 의도적이다. Spring AI가 감싸는 예외 타입은
     * 프로바이더·버전에 따라 달라지지만 429 응답 본문은 남는다. 대신 오탐 가능성이 있으므로
     * 판정 근거를 호출부가 로그로 남길 수 있게 문자열로 돌려준다.
     */
    private String quotaCauseOf(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("429") || lower.contains("resource_exhausted")
                        || lower.contains("quota") || lower.contains("rate limit")) {
                    return cause.getClass().getSimpleName() + ": "
                            + message.substring(0, Math.min(message.length(), 300));
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    private void sleep() {
        try {
            Thread.sleep(CALL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}