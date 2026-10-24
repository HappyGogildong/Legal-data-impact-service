import type { Metadata } from "next";
import { ConsentScreen } from "@/components/auth/consent-screen";

export const metadata: Metadata = {
  title: "프로필 동의",
  robots: { index: false },
};

export default function ConsentPage() {
  return <ConsentScreen />;
}
