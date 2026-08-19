import type { NextConfig } from "next";

/**
 * 백엔드 프록시.
 *
 * Vercel(HTTPS) 페이지에서 EC2(HTTP)를 브라우저가 직접 부르면 혼합 콘텐츠로 차단된다.
 * Next 서버가 대신 호출하면 서버 간 통신이라 차단되지 않는다.
 * 덕분에 백엔드에 도메인·인증서·nginx가 필요 없고 CORS도 필요 없다(같은 오리진).
 *
 * BACKEND_ORIGIN 은 서버에서만 읽는다 — NEXT_PUBLIC_ 접두사가 없으므로
 * 백엔드 주소가 브라우저 번들에 노출되지 않는다.
 */
const nextConfig: NextConfig = {
  async rewrites() {
    const backend = process.env.BACKEND_ORIGIN;
    if (!backend) return [];
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default nextConfig;
