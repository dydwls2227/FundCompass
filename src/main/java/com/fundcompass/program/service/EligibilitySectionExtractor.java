package com.fundcompass.program.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EligibilitySectionExtractor {

    // 섹션 헤더 — 제외·제한 대상도 자격 조건
    private static final Pattern SECTION_ANCHOR = Pattern.compile(
            "지원\\s?대상|신청\\s?대상|모집\\s?대상|지원\\s?자격|신청\\s?자격|참가\\s?자격"
                    + "|지원\\s?조건|자격\\s?요건|지원\\s?요건|참여\\s?대상|참여\\s?자격|참여\\s?요건"
                    + "|신청\\s?제외|지원\\s?제외|참여\\s?제한|제외\\s?대상|참가\\s?제한");
    private static final int SECTION_BEFORE = 100;
    private static final int SECTION_AFTER = 2_500;

    // 정량 조건 — 키워드 단독이 아니라 "숫자 + 비교어"가 붙어 있을 때만 조건
    private static final Pattern METRIC_ANCHOR = Pattern.compile(
            "(업력|매출|근로자|고용|종업원|직원|창업)"
                    + "[^\\n]{0,30}?"
                    + "\\d+\\s?(년|개월|억|천만|백만|명|인)\\s?원?\\s?(이내|이상|미만|이하|초과)");
    private static final int METRIC_BEFORE = 200;
    private static final int METRIC_AFTER = 400;

    // 이 지점부터는 신청서·사업계획서·동의서 양식이라 자격요건이 없다
    private static final Pattern FORM_SECTION = Pattern.compile(
            "\\[\\s?붙\\s?임|\\[\\s?서\\s?식|\\[\\s?별\\s?첨|붙임\\s?\\d|서식\\s?\\d|별첨\\s?\\d"
                    + "|개인\\s?\\(신용\\)\\s?정보|정보의\\s?수집");

    private static final int MAX_LENGTH = 8_000;
    private static final String GAP = "\n[...]\n";

    public String extract(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String body = text;

        List<int[]> windows = new ArrayList<>();
        collect(windows, body, SECTION_ANCHOR, SECTION_BEFORE, SECTION_AFTER);
        collect(windows, body, METRIC_ANCHOR, METRIC_BEFORE, METRIC_AFTER);

        if (windows.isEmpty()) {
            return "";
        }
        return join(body, merge(windows));
    }

    private void collect(List<int[]> target, String text, Pattern anchor, int before, int after) {
        Matcher matcher = anchor.matcher(text);
        while (matcher.find()) {
            int start = Math.max(0, matcher.start() - before);
            int end = Math.min(text.length(), matcher.end() + after);
            target.add(new int[]{start, boundedEnd(text, matcher.end(), end)});
        }
    }

    private int boundedEnd(String text, int from, int end) {
        Matcher matcher = FORM_SECTION.matcher(text).region(from, end);
        return matcher.find() ? matcher.start() : end;
    }

    private List<int[]> merge(List<int[]> windows) {
        windows.sort(Comparator.comparingInt(window -> window[0]));

        List<int[]> merged = new ArrayList<>();
        for (int[] window : windows) {
            int[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && window[0] <= last[1]) {
                last[1] = Math.max(last[1], window[1]);   // 겹치거나 붙어 있으면 확장
            } else {
                merged.add(new int[]{window[0], window[1]});
            }
        }
        return merged;
    }

    private String join(String text, List<int[]> windows) {
        StringBuilder joined = new StringBuilder();
        for (int[] window : windows) {
            if (joined.length() >= MAX_LENGTH) {
                break;
            }
            if (!joined.isEmpty()) {
                joined.append(GAP);
            }
            joined.append(text, window[0], window[1]);
        }
        return cut(joined.toString());
    }

    private String cut(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
    }
}