import type { Metadata } from "next";
import { Suspense } from "react";
import { HistoryDetailView } from "@/components/history/history-detail-view";

export const metadata: Metadata = {
  title: "이력",
  robots: { index: false },
};

/** useParams 는 정적 껍데기 밖에서 — Suspense 경계(Cache Components). */
export default function HistoryDetailPage() {
  return (
    <Suspense fallback={null}>
      <HistoryDetailView />
    </Suspense>
  );
}
