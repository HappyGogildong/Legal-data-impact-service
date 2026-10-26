import type { Metadata } from "next";
import { connection } from "next/server";
import { Suspense } from "react";
import { LawListView } from "@/components/laws/law-list-view";
import { LawTabs } from "@/components/laws/law-tabs";
import {
  TRENDING_PAGE_SIZE,
  TrendingListView,
} from "@/components/laws/trending-list-view";
import { fetchTrendingLaws, fetchUpcomingLaws } from "@/lib/api/laws";
import { seoulToday } from "@/lib/dates";
import {
  LAW_PAGE_SIZE,
  parseLawTab,
  parseRange,
  parseTrendingWindow,
  rangeBounds,
} from "@/lib/law-view";

export const metadata: Metadata = { title: "법령 목록" };

/** S06 법령 목록 — 탭 곧 시행·자주 찾는(`?tab=trending`). 조회 실패는 error(S16). */
export default function LawsPage({ searchParams }: PageProps<"/laws">) {
  return (
    <div className="mx-auto max-w-[720px] px-4 py-5">
      <h1 className="mb-2 text-lg font-bold">법령 목록</h1>
      <Suspense
        fallback={
          <p className="text-sm text-muted-foreground">
            목록을 불러오는 중이에요…
          </p>
        }
      >
        <LawListSection searchParams={searchParams} />
      </Suspense>
    </div>
  );
}

async function LawListSection({
  searchParams,
}: Pick<PageProps<"/laws">, "searchParams">) {
  const params = await searchParams;
  await connection();
  const tab = parseLawTab(params.tab);
  const today = seoulToday();
  if (tab === "trending") {
    const window = parseTrendingWindow(params.window);
    const page = await fetchTrendingLaws(window, TRENDING_PAGE_SIZE);
    return (
      <>
        <LawTabs tab={tab} />
        <TrendingListView
          window={window}
          today={today}
          items={page.items ?? []}
        />
      </>
    );
  }
  const range = parseRange(params.range);
  const bounds = rangeBounds(range, today);
  const firstPage = await fetchUpcomingLaws({
    ...bounds,
    limit: LAW_PAGE_SIZE,
  });
  return (
    <>
      <LawTabs tab={tab} />
      <LawListView
        range={range}
        bounds={bounds}
        today={today}
        firstPage={firstPage}
      />
    </>
  );
}
