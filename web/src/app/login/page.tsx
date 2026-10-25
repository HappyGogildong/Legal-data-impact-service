import type { Metadata } from "next";
import { Suspense } from "react";
import { LoginScreen } from "@/components/auth/login-screen";

export const metadata: Metadata = { title: "로그인", robots: { index: false } };

/** useSearchParams 는 정적 껍데기 밖에서 — Suspense 경계(Cache Components). */
export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginScreen />
    </Suspense>
  );
}
