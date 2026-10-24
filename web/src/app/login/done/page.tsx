import type { Metadata } from "next";
import { LoginDoneScreen } from "@/components/auth/login-done-screen";

export const metadata: Metadata = { title: "로그인", robots: { index: false } };

export default function LoginDonePage() {
  return <LoginDoneScreen />;
}
