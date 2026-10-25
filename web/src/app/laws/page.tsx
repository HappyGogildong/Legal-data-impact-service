import type { Metadata } from "next";
import { connection } from "next/server";
import { Suspense } from "react";
import { LawListView } from "@/components/laws/law-list-view";
import { fetchUpcomingLaws } from "@/lib/api/laws";
import { seoulToday } from "@/lib/dates";
import { LAW_PAGE_SIZE, parseRange, rangeBounds } from "@/lib/law-view";

export const metadata: Metadata = { title: "곧 시행되는 법령" };

/** S06 법령 목록 — 곧 시행 탭만("자주 찾는"은 다음 하위 프로젝트). 조회 실패는 error(S16). */
export default function LawsPage({ searchParams }: PageProps<"/laws">) {
  return (
    <div className="mx-auto max-w-[720px] px-4 py-5">
      <h1 className="mb-3 text-lg font-bold">곧 시행되는 법령</h1>
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
  const { range: rangeParam } = await searchParams;
  await connection();
  const range = parseRange(rangeParam);
  const bounds = rangeBounds(range, seoulToday());
  const firstPage = await fetchUpcomingLaws({
    ...bounds,
    limit: LAW_PAGE_SIZE,
  });
  return (
    <LawListView
      range={range}
      bounds={bounds}
      today={bounds.from}
      firstPage={firstPage}
    />
  );
}
