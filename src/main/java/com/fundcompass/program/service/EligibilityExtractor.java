package com.fundcompass.program.service;

import com.fundcompass.program.domain.Program;
import com.fundcompass.program.domain.ProgramDocument;
import com.fundcompass.program.infra.dto.EligibilityExtraction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EligibilityExtractor {

    /**
     * 아래 {@link #SYSTEM_PROMPT}를 고치면 <b>반드시 함께 올린다.</b>
     * 버전이 그대로면 서로 다른 프롬프트로 뽑힌 행이 같은 라벨을 달아 비교가 불가능해진다.
     *
     * <p>배치 서비스에 두었다가 실제로 한 번 놓쳤다(v4 라벨에 두 가지 프롬프트가 섞였다).
     * 고칠 파일과 올릴 값이 떨어져 있는 것이 원인이라 프롬프트 바로 옆으로 옮겼다.
     */
    public static final String PROMPT_VERSION = "v5";

    private static final String SYSTEM_PROMPT = """
            너는 정부 지원사업 공고문에서 신청 자격요건을 추출하는 도구다.

            [핵심 원칙]
            1. 문서에 명시되지 않은 조건은 반드시 UNKNOWN으로 둔다. 추론하거나 일반 상식으로 채우지 않는다.
            2. NOT_REQUIRED는 "제한 없음", "전 업종", "지역 무관"처럼 조건이 없다고 문서가
               명시한 경우에만 쓰며, 그 문장을 evidence에 반드시 적는다.
               근거 문장을 댈 수 없으면 UNKNOWN이다.
            3. evidence는 그 조건이 적혀 있는 문서의 문장을 그대로 복사한다.
               답을 다시 적는 칸이 아니라, 문서의 어디를 보고 그렇게 판단했는지 가리키는 칸이다.

               (X) values=["중소기업"]  evidence="중소기업"
               (O) values=["중소기업"]  evidence="ㅇ 지원대상: 대구지역 내 본사를 둔 중소기업"

               - values 에 넣은 낱말이나 숫자를 그대로 옮겨 적지 말 것.
                 그 낱말을 읽어낸 문장을 통째로 인용한다.
               - 조건이 적힌 문장 하나면 충분하다. 그 문장이 두세 줄에 걸쳐 있으면 함께 인용한다.
                 항목이 ①②③처럼 여러 개 나열돼 있어도 전부 옮기지 말고, 조건을 가장 잘 보여주는
                 하나만 고른다. 목록 전체를 붙여넣지 말 것.
               - 문서의 서로 다른 자리에 있는 문장을 이어붙이지 말 것.
               - 한 글자도 바꾸지 말 것. 조사·띄어쓰기·기호까지 문서 그대로 옮긴다.
                 문서가 "자원의 절약과 재활용촉진"이면 "자원의 절약 및 재활용촉진"으로 고쳐 적으면 안 된다.
                 문서의 표기가 어색하거나 틀린 것처럼 보여도 그대로 둔다.
               - 표에서 읽은 값은 주의한다. 표의 칸 내용은 원문에서 서로 떨어져 있을 수 있다.
                 원문에 연속으로 존재하는 구간만 인용한다. 조각을 이어 문장을 만들지 말 것.
               - status 가 UNKNOWN 이면 evidence 는 null 이다. "UNKNOWN" 이라고 적지 말 것.

            [자격요건이 아닌 것 — 추출하지 말 것]
            - 평가지표·배점표의 항목 (예: "전년도 매출액 25점", "상시고용인원 25점")
            - 신청서·사업계획서 양식의 빈칸과 체크박스 (예: "□ 근무경력 5년이상")
            - 지원 금액·한도 (예: "최대 480만원 지원")
            - 사업기간·협약기간·하자보증기간
            - 심사위원·멘토·평가위원의 자격
            - 신청 제외·참여 제한 대상은 어떤 필드에도 넣지 않는다.
              "~에 해당하는 기업은 제외", "~인 경우 신청 불가" 처럼 배제를 뜻하는 문장이면
              그 안의 업종·지역·수치를 조건으로 옮기지 말 것.
              (예: "주된 업종이 일반유흥주점업에 해당하는 기업"은 제외 대상이므로
               industries 에 넣으면 의미가 정반대가 된다)
            - "우선 지원", "우선 선정", "우대", "가점" 대상
              (해당하지 않아도 신청할 수 있으면 자격요건이 아니다)

            [필드 판단 기준]
            - "창업 7년 이내", "업력 3년 미만"      -> businessAge   (단위: 년)
            - "전년도 매출액 10억원 이하"           -> annualRevenue (단위: 억원)
            - "상시근로자 5인 미만", "고용 10인 미만" -> employeeCount (단위: 명)
            - 사업장 소재지 조건                    -> regions
            - 한국표준산업분류 등 업종 조건          -> industries
            - "중소기업", "소상공인", "예비창업자"    -> businessTypes
            - 조건이 긍정문("~인 기업")인지 배제문("~인 기업은 제외")인지 먼저 판단한 뒤 채운다.

            [단위 변환]
            - 매출액은 억원 단위 실수로 변환한다. "5천만원 이하" -> max=0.5
            - 업력이 개월이면 년으로 변환한다. "18개월 이내" -> max=1.5
            
            [경계 처리]
            - "이하", "이내", "이상"  -> Inclusive = true
            - "미만", "초과"          -> Inclusive = false
            - 숫자를 조정하지 말 것. "10명 미만"은 max=10, maxInclusive=false 로 적는다.max=9 처럼 값을 바꾸면 안 된다.
            - 같은 조건이 여러 번 다르게 적혀 있으면 더 엄격한 쪽을 택한다.
            """;

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern EXTRA_SPACE = Pattern.compile("[ \\t]{2,}");

    private final ChatClient chatClient;
    private final EligibilitySectionExtractor sectionExtractor;

    public EligibilityExtractor(ChatClient.Builder builder,
                                EligibilitySectionExtractor sectionExtractor) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.sectionExtractor = sectionExtractor;
    }

    public EligibilityExtraction extract(Program program, List<ProgramDocument> documents) {
        String prompt = buildPrompt(program, documents);
        log.info("자격요건 추출 요청 - programId={}, 프롬프트 {}자", program.getId(), prompt.length());

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(EligibilityExtraction.class);
    }

    String buildPrompt(Program program, List<ProgramDocument> documents) {
        StringBuilder prompt = new StringBuilder();
        append(prompt, "공고명", program.getName());
        append(prompt, "지원대상 분류", program.getTargetName());
        append(prompt, "분야", program.getFieldLarge());
        append(prompt, "해시태그", program.getHashtags());
        append(prompt, "소관기관", program.getSupervisingInstitution());
        append(prompt, "사업개요", stripHtml(program.getSummaryHtml()));

        for (ProgramDocument document : documents) {
            append(prompt, "첨부문서: " + document.getFileName(),
                    sectionExtractor.extract(document.getExtractedText()));
        }
        return prompt.toString();
    }

    private void append(StringBuilder prompt, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        prompt.append('[').append(label).append("]\n").append(value).append("\n\n");
    }

    String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = HTML_TAG.matcher(html).replaceAll(" ");
        text = HtmlUtils.htmlUnescape(text).replace('\u00A0', ' ');
        return EXTRA_SPACE.matcher(text).replaceAll(" ").trim();
    }
}
