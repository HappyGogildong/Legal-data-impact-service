import type { Metadata } from "next";
import { HistoryListView } from "@/components/history/history-list-view";

export const metadata: Metadata = {
  title: "이력",
  robots: { index: false },
};

export default function HistoryPage() {
  return <HistoryListView />;
}
