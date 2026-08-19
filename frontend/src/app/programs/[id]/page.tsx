"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Page } from "@/components/shell";
import { ConditionRow, DeadlineText, VerdictBadge } from "@/components/verdict";
import { fetchExplanation, fetchProgram } from "@/lib/api";
import { loadProfile } from "@/lib/profile";
import type { ExplanationResponse, ProgramMatch } from "@/lib/types";

export default function ProgramDetail() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [match, setMatch] = useState<ProgramMatch | null>(null);
  const [explanation, setExplanation] = useState<ExplanationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const profile = loadProfile();
    if (!profile) {
      router.replace("/");
      return;
    }
    const programId = Number(id);

    // 판정(즉시)과 설명(LLM 호출)을 분리한다. 체크리스트를 먼저 그리고
    // 설명은 도착하는 대로 채운다 — 설명이 느리다고 화면이 비어 있으면 안 된다
    fetchProgram(programId, profile)
      .then(setMatch)
      .catch(() => setError("공고를 불러오지 못했습니다."));

    fetchExplanation(programId, profile)
      .then(setExplanation)
      .catch(() => setExplanation(null));
  }, [id, router]);

  if (error) {
    return (
      <Page>
        <p className="rounded-xl border border-border bg-surface p-6 text-sm text-ink-soft">{error}</p>
        <BackLink />
      </Page>
    );
  }

  if (!match) {
    return (
      <Page>
        <div className="space-y-3">
          <div className="h-6 w-3/4 animate-pulse rounded bg-mute-soft" />
          <div className="h-40 animate-pulse rounded-xl bg-mute-soft" />
        </div>
      </Page>
    );
  }

  const satisfied = match.conditions.filter((c) => c.verdict === "SATISFIED");
  const violated = match.conditions.filter((c) => c.verdict === "VIOLATED");
  const actionable = match.conditions.filter(
    (c) => c.unknownReason === "PROFILE_MISSING" || c.unknownReason === "BAND_STRADDLES_BOUNDARY",
  );
  const notStated = match.conditions.filter(
    (c) => c.verdict === "UNKNOWN" && !actionable.includes(c),
  );

  return (
    <Page>
      <BackLink />

      <header className="mt-3">
        <div className="flex items-start justify-between gap-3">
          <h1 className="text-xl font-bold leading-snug tracking-tight text-ink">{match.name}</h1>
          <VerdictBadge verdict={match.verdict} />
        </div>
        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-faint">
          {match.institution && <span>{match.institution}</span>}
          <DeadlineText daysLeft={match.daysLeft} endDate={match.applyEndDate} closed={match.closed} />
        </div>
      </header>

      {match.closed && (
        <p className="mt-4 rounded-xl bg-mute-soft px-4 py-3 text-sm font-medium text-mute">
          이 공고는 {match.applyEndDate}에 마감되었습니다. 아래 판정은 참고용입니다.
        </p>
      )}

      {match.eligibleFrom && (
        <p className="mt-4 rounded-xl bg-warn-soft px-4 py-3 text-sm font-medium text-warn">
          지금은 조건이 맞지 않지만 {match.eligibleFrom}부터 신청할 수 있습니다.
        </p>
      )}

      <section className="mt-5 rounded-2xl border border-border bg-surface p-5">
        <h2 className="text-sm font-semibold text-ink">판정 요약</h2>
        {explanation === null ? (
          <div className="mt-2 space-y-2" aria-label="설명 생성 중">
            <div className="h-3.5 w-full animate-pulse rounded bg-mute-soft" />
            <div className="h-3.5 w-5/6 animate-pulse rounded bg-mute-soft" />
          </div>
        ) : explanation.generated && explanation.explanation ? (
          <p className="mt-2 whitespace-pre-line text-[15px] leading-relaxed text-ink-soft">
            {explanation.explanation}
          </p>
        ) : (
          <p className="mt-2 text-sm text-ink-faint">
            {explanation.note ?? "아래 조건별 결과를 참고하세요."}
          </p>
        )}
      </section>

      <section className="mt-5 rounded-2xl border border-border bg-surface p-5">
        <div className="flex items-baseline justify-between">
          <h2 className="text-sm font-semibold text-ink">조건별 판정</h2>
          <span className="text-xs text-ink-faint">
            {match.judgedCount}개 조건으로 판정
          </span>
        </div>

        {violated.length > 0 && <Group title="맞지 않는 조건" items={violated} />}
        {satisfied.length > 0 && <Group title="충족한 조건" items={satisfied} />}
        {actionable.length > 0 && (
          <Group
            title="입력하면 더 정확해집니다"
            hint="이 항목을 채우면 판정이 확실해집니다"
            items={actionable}
          />
        )}
        {notStated.length > 0 && (
          <details className="mt-4 border-t border-border pt-3">
            <summary className="cursor-pointer text-xs text-ink-faint hover:text-ink-soft">
              공고가 조건을 명시하지 않은 항목 {notStated.length}개
            </summary>
            <ul className="mt-1">
              {notStated.map((c) => <ConditionRow key={c.field} condition={c} />)}
            </ul>
          </details>
        )}
      </section>

      {match.detailUrl && (
        <a
          href={match.detailUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-5 block rounded-xl bg-brand px-4 py-3.5 text-center font-semibold text-white transition hover:opacity-90"
        >
          기업마당에서 공고문 원문 보기
        </a>
      )}
      <p className="mt-3 text-center text-xs leading-relaxed text-ink-faint">
        판정은 공고문에서 자동으로 뽑은 조건에 근거합니다.
        <br />
        놓친 조건이 있을 수 있으니 신청 전 원문을 확인해 주세요.
      </p>
    </Page>
  );
}

function Group({
  title, hint, items,
}: {
  title: string; hint?: string; items: React.ComponentProps<typeof ConditionRow>["condition"][];
}) {
  return (
    <div className="mt-4">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-ink-faint">{title}</h3>
      {hint && <p className="mt-0.5 text-xs text-ink-faint">{hint}</p>}
      <ul className="mt-1">
        {items.map((c) => <ConditionRow key={c.field} condition={c} />)}
      </ul>
    </div>
  );
}

function BackLink() {
  return (
    <Link href="/results" className="text-sm text-brand underline-offset-4 hover:underline">
      ← 결과 목록
    </Link>
  );
}
