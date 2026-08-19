import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "자금나침반 — 검색이 아니라 판정합니다",
  description:
    "사업자 정보를 입력하면 정부 지원사업 1,400여 건 중 신청 가능한 것을 찾아 근거와 함께 알려드립니다.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#1e4d5c",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body className="min-h-dvh bg-bg antialiased">{children}</body>
    </html>
  );
}
