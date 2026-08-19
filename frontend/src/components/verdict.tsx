import type { ConditionResult, EligibilityVerdict } from "@/lib/types";

export const VERDICT_LABEL: Record<EligibilityVerdict, string> = {
  ELIGIBLE: "신청 가능",
  NEEDS_REVIEW: "확인 필요",
  INELIGIBLE: "신청 불가",
};

const VERDICT_STYLE: Record<EligibilityVerdict, string> = {
  ELIGIBLE: "bg-ok-soft text-ok",
  NEEDS_REVIEW: "bg-warn-soft text-warn",
  INELIGIBLE: "bg-mute-soft text-mute",
};

export function VerdictBadge({ verdict }: { verdict: EligibilityVerdict }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full px-2.5 py-1 text-xs font-semibold ${VERDICT_STYLE[verdict]}`}
    >
      {VERDICT_LABEL[verdict]}
    </span>
  );
}

/**
 * 조건 한 줄. 기획서가 "데모의 하이라이트"로 꼽은 체크리스트의 구성 요소다.
 *
 * 미상을 두 갈래로 나눠 보여준다. 공고가 조건을 걸지 않은 것(대다수)은 사용자가
 * 할 일이 없으므로 조용히 표시하고, 프로필이 비어서 못 본 것만 행동을 요구한다.
 */
export function ConditionRow({ condition }: { condition: ConditionResult }) {
  const actionable =
    condition.unknownReason === "PROFILE_MISSING" ||
    condition.unknownReason === "BAND_STRADDLES_BOUNDARY";

  const mark =
    condition.verdict === "SATISFIED" ? (
      <span aria-hidden className="text-ok">✓</span>
    ) : condition.verdict === "VIOLATED" ? (
      <span aria-hidden className="text-mute">✕</span>
    ) : (
      <span aria-hidden className={actionable ? "text-warn" : "text-ink-faint"}>–</span>
    );

  const status =
    condition.verdict === "SATISFIED" ? "충족"
    : condition.verdict === "VIOLATED" ? "미충족"
    : actionable ? "입력 필요"
    : "공고에 조건 없음";

  return (
    <li className="flex gap-3 border-b border-border py-3 last:border-0">
      <span className="mt-0.5 w-4 shrink-0 text-center font-semibold">{mark}</span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-x-2">
          <span className="font-semibold text-ink">{condition.label}</span>
          <span
            className={
              condition.verdict === "SATISFIED" ? "text-xs text-ok"
              : condition.verdict === "VIOLATED" ? "text-xs text-mute"
              : actionable ? "text-xs text-warn"
              : "text-xs text-ink-faint"
            }
          >
            {status}
          </span>
        </div>
        {(condition.requirement || condition.actual) && (
          <p className="mt-0.5 text-sm text-ink-soft">
            {condition.requirement && <span>요구: {condition.requirement}</span>}
            {condition.requirement && condition.actual && (
              <span className="text-ink-faint"> · </span>
            )}
            {condition.actual && <span>내 정보: {condition.actual}</span>}
          </p>
        )}
        {condition.eligibleFrom && (
          <p className="mt-1 text-sm font-medium text-warn">
            {condition.eligibleFrom}부터 신청할 수 있습니다
          </p>
        )}
        {condition.evidence && (
          <details className="mt-1.5">
            <summary className="cursor-pointer text-xs text-ink-faint hover:text-ink-soft">
              공고문 근거 보기
            </summary>
            <blockquote className="mt-1.5 border-l-2 border-border pl-3 text-xs leading-relaxed whitespace-pre-line text-ink-soft">
              {condition.evidence}
            </blockquote>
          </details>
        )}
      </div>
    </li>
  );
}

export function DeadlineText({
  daysLeft, endDate, closed,
}: { daysLeft: number | null; endDate: string | null; closed?: boolean }) {
  if (endDate == null || daysLeft == null) {
    return <span className="text-ink-faint">상시·기한 미정</span>;
  }
  // 음수를 그대로 그리면 "D--1"이 된다. 마감된 공고다
  if (closed || daysLeft < 0) return <span className="text-mute">마감됨</span>;
  if (daysLeft === 0) return <span className="font-semibold text-warn">오늘 마감</span>;
  if (daysLeft <= 7) return <span className="font-semibold text-warn tnum">D-{daysLeft}</span>;
  return <span className="text-ink-faint tnum">D-{daysLeft}</span>;
}
