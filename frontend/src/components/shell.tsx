import Link from "next/link";
import type { ReactNode } from "react";

export function Header() {
  return (
    <header className="border-b border-border bg-surface">
      <div className="mx-auto flex max-w-3xl items-center justify-between px-5 py-3.5">
        <Link href="/" className="flex items-center gap-2">
          <span aria-hidden className="text-lg">🧭</span>
          <span className="font-bold tracking-tight text-brand">자금나침반</span>
        </Link>
        <span className="hidden text-xs text-ink-faint sm:block">
          검색이 아니라 판정합니다
        </span>
      </div>
    </header>
  );
}

export function Page({ children }: { children: ReactNode }) {
  return (
    <>
      <Header />
      <main className="mx-auto max-w-3xl px-5 pb-24 pt-7">{children}</main>
      <footer className="border-t border-border px-5 py-6 text-center text-xs leading-relaxed text-ink-faint">
        공고 정보 출처: 기업마당 · 판정 결과는 참고용이며 최종 자격은 공고문 원문을 확인하세요
      </footer>
    </>
  );
}
