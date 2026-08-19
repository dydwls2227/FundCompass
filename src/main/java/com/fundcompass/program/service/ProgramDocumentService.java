package com.fundcompass.program.service;

import com.fundcompass.program.domain.*;
import com.fundcompass.program.infra.FileDownloader;
import com.fundcompass.program.infra.HwpTextExtractor;
import com.fundcompass.program.infra.PdfTextExtractor;
import com.fundcompass.program.repository.ProgramDocumentRepository;
import com.fundcompass.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramDocumentService {

    private static final long DOWNLOAD_INTERVAL_MS = 200;
    private static final String FILE_SEPARATOR = "@";

    private final ProgramRepository programRepository;
    private final ProgramDocumentRepository documentRepository;
    private final FileDownloader fileDownloader;
    private final PdfTextExtractor pdfTextExtractor;
    private final HwpTextExtractor hwpTextExtractor;

    public record RegisterResult(int created, int skipped, int noFile) {}

    public record ExtractResult(int extracted, int unsupported, int failed) {}


    public RegisterResult registerDocuments() {
        int created = 0, skipped = 0, noFile = 0;

        for (Program program : programRepository.findAll()) {
            List<Candidate> candidates = collectDocuments(program);
            if (candidates.isEmpty()) {
                noFile++;
                continue;
            }
            for (Candidate candidate : candidates) {
                if (documentRepository.findByProgramIdAndFileUrl(
                        program.getId(), candidate.url()).isPresent()) {
                    skipped++;
                    continue;
                }
                documentRepository.save(ProgramDocument.builder()
                        .programId(program.getId())
                        .fileName(candidate.name())
                        .fileUrl(candidate.url())
                        .fileType(DocumentFileType.fromFileName(candidate.name()))
                        .status(ExtractionStatus.PENDING)
                        .build());
                created++;
            }
        }

        RegisterResult result = new RegisterResult(created, skipped, noFile);
        log.info("문서 등록 완료 - 신규 {}, 기존 {}, 첨부없음 {}", created, skipped, noFile);
        return result;
    }

    private List<Candidate> collectDocuments(Program program) {
        List<Candidate> candidates = new ArrayList<>();
        addAll(candidates, program.getAttachmentName(), program.getAttachmentUrl());
        addAll(candidates, program.getPrintFileName(), program.getPrintFileUrl());
        return candidates;
    }

    private void addAll(List<Candidate> target, String names, String urls) {
        if (urls == null || urls.isBlank()) {
            return;
        }
        String[] urlParts = urls.split(FILE_SEPARATOR);
        String[] nameParts = names == null ? new String[0] : names.split(FILE_SEPARATOR);

        for (int i = 0; i < urlParts.length; i++) {
            String url = urlParts[i].trim();
            if (url.isBlank()) {
                continue;
            }
            // 파일명 개수가 URL 개수와 다를 수 있으므로 방어적으로 접근
            String name = i < nameParts.length ? nameParts[i].trim() : null;
            target.add(new Candidate(name, url));
        }
    }

    private record Candidate(String name, String url) {}


    public ExtractResult extractPending(int limit) {
        List<ProgramDocument> targets = documentRepository
                .findByStatus(ExtractionStatus.PENDING).stream()
                .limit(limit)
                .toList();

        int extracted = 0, unsupported = 0, failed = 0;

        for (ProgramDocument document : targets) {
            if (!document.getFileType().isExtractable()) {
                document.markUnsupported();
                documentRepository.save(document);
                unsupported++;
                continue;
            }
            try {
                byte[] bytes = fileDownloader.download(document.getFileUrl());
                String text = extractText(document.getFileType(), bytes);

                // 빈 텍스트를 EXTRACTED로 찍으면 "성공했는데 내용이 없는" 행이 남는다.
                // 2-2에서 그렇게 쌓인 146건이 "텍스트 확보 910건"이라는 낙관적 수치를 만들었다
                if (text.isBlank()) {
                    document.markUnsupported();
                    documentRepository.save(document);
                    unsupported++;
                    sleep();
                    continue;
                }
                document.markExtracted(text);
                documentRepository.save(document);
                extracted++;
                sleep();
            } catch (Exception e) {
                failed++;
                log.warn("추출 실패 (documentId={}): {}", document.getId(), e.getMessage());
                try {
                    document.markFailed(e.getMessage());
                    documentRepository.save(document);
                } catch (Exception ignored) {
                }
            }
        }

        ExtractResult result = new ExtractResult(extracted, unsupported, failed);
        log.info("텍스트 추출 완료 - 성공 {}, 미지원 {}, 실패 {}", extracted, unsupported, failed);
        return result;
    }

    private String extractText(DocumentFileType fileType, byte[] bytes) throws Exception {
        return switch (fileType) {
            case PDF -> pdfTextExtractor.extract(bytes);
            case HWP -> hwpTextExtractor.extract(bytes);
            default -> throw new IllegalStateException("추출 대상이 아닌 형식: " + fileType);
        };
    }

    /**
     * 이미 {@code UNSUPPORTED}로 확정된 문서를 다시 추출 대기로 돌린다.
     *
     * <p>파서를 새로 붙이면 "지원하지 않음"의 의미가 달라진다. HWP 1,781건이 그 경우다.
     * 마이그레이션으로 한 번에 UPDATE하지 않고 메서드로 둔 이유는, 파서를 추가할 때마다
     * 같은 일이 반복되고 <b>몇 건이 되돌려졌는지 확인하며</b> 실행하는 편이 안전하기 때문이다.
     *
     * @return 되돌린 문서 수
     */
    public int reopenUnsupported(DocumentFileType fileType) {
        List<ProgramDocument> targets = documentRepository
                .findByFileTypeAndStatus(fileType, ExtractionStatus.UNSUPPORTED);

        targets.forEach(ProgramDocument::markPending);
        documentRepository.saveAll(targets);

        log.info("추출 재개 대상으로 되돌림 - {} {}건", fileType, targets.size());
        return targets.size();
    }

    /**
     * {@code EXTRACTED}인데 텍스트가 빈 문서를 추출 대기로 돌린다.
     *
     * <p>과거에 {@code markExtracted("")}가 성공으로 기록되던 탓에 남은 행이다.
     * 재추출하면 지금 코드가 {@code UNSUPPORTED}로 정확히 분류한다.
     *
     * <p>{@code findTargets()}가 이미 빈 텍스트를 걸러내므로 기능상 급하지는 않다.
     * 다만 상태값이 거짓이면 "텍스트 확보 몇 건"을 셀 때마다 낙관적 수치가 나온다
     * (2-1의 "910건"이 실제로는 832건이었던 것이 이 때문이다).
     *
     * @return 되돌린 문서 수
     */
    public int reopenEmptyExtracted() {
        List<ProgramDocument> targets = documentRepository
                .findByStatus(ExtractionStatus.EXTRACTED).stream()
                .filter(document -> document.getExtractedText() == null
                        || document.getExtractedText().isBlank())
                .toList();

        targets.forEach(ProgramDocument::markPending);
        documentRepository.saveAll(targets);

        log.info("빈 텍스트 문서를 추출 대기로 되돌림 - {}건", targets.size());
        return targets.size();
    }

    private void sleep() {
        try {
            Thread.sleep(DOWNLOAD_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}