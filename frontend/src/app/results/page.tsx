"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Page } from "@/components/shell";
import { DeadlineText, VERDICT_LABEL, VerdictBadge } from "@/components/verdict";
import { fetchMatches } from "@/lib/api";
import { loadProfile } from "@/lib/profile";
import type { EligibilityVerdict, MatchResponse, Profile } from "@/lib/types";

const TABS: EligibilityVerdict[] = ["ELIGIBLE", "NEEDS_REVIEW", "INELIGIBLE"];

const TAB_NOTE: Record<EligibilityVerdict, string> = {
  ELIGIBLE: "확인한 조건을 모두 충족했습니다.",
  NEEDS_REVIEW: "확인한 조건은 충족했지만 공고가 명시하지 않은 조건이 있어 단정할 수 없습니다.",
  INELIGIBLE: "조건이 명확히 맞지 않습니다. 대부분 사업장 지역이 다른 경우입니다.",
};

export default function Results() {
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [tab, setTab] = useState<EligibilityVerdict>("ELIGIBLE");
  const [data, setData] = useState<MatchResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const saved = loadProfile();
    if (!saved) {
      router.replace("/");
      return;
    }
    setProfile(saved);
  }, [router]);

  const load = useCallback(async (p: Profile, verdict: EligibilityVerdict) => {
    setLoading(true);
    setError(null);
    try {
      setData(await fetchMatches(p, verdict, 30));
    } catch {
      setError("결과를 불러오지 못했습니다. 서버가 실행 중인지 확인해 주세요.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (profile) void load(profile, tab);
  }, [profile, tab, load]);

  return (
    <Page>
      <div className="mb-5 flex items-baseline justify-between gap-3">
        <h1 className="text-xl font-bold tracking-tight text-ink">매칭 결과</h1>
        <Link href="/" className="shrink-0 text-sm text-brand underline-offset-4 hover:underline">
          조건 수정
        </Link>
      </div>

      <div role="tablist" aria-label="판정별 결과" className="mb-4 grid grid-cols-3 gap-2">
        {TABS.map((v) => {
          const on = tab === v;
          const count = data?.counts?.[v];
          return (
            <button
              key={v}
              role="tab"
              aria-selected={on}
              onClick={() => setTab(v)}
              className={`rounded-xl border px-3 py-2.5 text-center transition ${
                on ? "border-brand bg-brand text-white" : "border-border bg-surface hover:border-brand/40"
              }`}
            >
              <span className="block text-xs">{VERDICT_LABEL[v]}</span>
              <span className={`tnum block text-lg font-bold ${on ? "text-white" : "text-ink"}`}>
                {count ?? "–"}
              </span>
            </button>
          );
        })}
      </div>

      <p className="mb-4 text-sm text-ink-soft">{TAB_NOTE[tab]}</p>

      {loading && <SkeletonList />}

      {error && (
        <p className="rounded-xl border border-border bg-surface p-5 text-sm text-ink-soft">{error}</p>
      )}

      {!loading && !error && data && data.items.length === 0 && (
        <p className="rounded-xl border border-border bg-surface p-8 text-center text-sm text-ink-soft">
          이 분류에 해당하는 공고가 없습니다.
        </p>
      )}

      {!loading && !error && data && data.items.length > 0 && (
        <ul className="space-y-3">
          {data.items.map((m) => (
            <li key={m.programId}>
              <Link
                href={`/programs/${m.programId}`}
                className="block rounded-xl border border-border bg-surface p-4 transition hover:border-brand/40"
              >
                <div className="flex items-start justify-between gap-3">
                  <h2 className="font-semibold leading-snug text-ink">{m.name}</h2>
                  <VerdictBadge verdict={m.verdict} />
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-faint">
                  {m.institution && <span>{m.institution}</span>}
                  <DeadlineText daysLeft={m.daysLeft} endDate={m.applyEndDate} closed={m.closed} />
                  <span>근거 {m.judgedCount}개 조건</span>
                </div>
                {m.eligibleFrom && (
                  <p className="mt-2 text-sm font-medium text-warn">
                    {m.eligibleFrom}부터 신청 가능해집니다
                  </p>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}

      {data && data.excludedClosed > 0 && (
        <p className="mt-5 text-center text-xs text-ink-faint">
          이미 마감된 공고 <span className="tnum">{data.excludedClosed}</span>건은 제외했습니다
        </p>
      )}
    </Page>
  );
}

function SkeletonList() {
  return (
    <ul className="space-y-3" aria-label="불러오는 중">
      {[0, 1, 2].map((i) => (
        <li key={i} className="rounded-xl border border-border bg-surface p-4">
          <div className="h-4 w-3/4 animate-pulse rounded bg-mute-soft" />
          <div className="mt-2.5 h-3 w-1/2 animate-pulse rounded bg-mute-soft" />
        </li>
      ))}
    </ul>
  );
}
