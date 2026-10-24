import type { Metadata } from "next";
import { AskScreen } from "@/components/analysis/ask-screen";

export const metadata: Metadata = {
  title: "분석 결과",
  robots: { index: false },
};

export default function AskPage() {
  return <AskScreen />;
}
