package com.fundcompass.program.service;

import com.fundcompass.program.domain.*;
import com.fundcompass.program.infra.FileDownloader;
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
                String text = pdfTextExtractor.extract(bytes);
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

    private void sleep() {
        try {
            Thread.sleep(DOWNLOAD_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}