"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import Link from "next/link";
import { fetchLawPage } from "@/lib/api/law-list";
import type { LawListPage } from "@/lib/api/types";
import { LAW_PAGE_SIZE, RANGE_KEYS, RANGES, type Range } from "@/lib/law-view";
import { cn } from "@/lib/utils";
import { LawRow } from "./law-row";

type LawListViewProps = {
  range: Range;
  bounds: { from: string; to: string };
  today: string;
  firstPage: LawListPage;
};

/** S06 곧 시행 — 기간 필터(링크)·목록·"더 보기"(다음 쪽을 브라우저에서 이어 붙인다). 첫 쪽은 서버 렌더링. */
export function LawListView({
  range,
  bounds,
  today,
  firstPage,
}: LawListViewProps) {
  const query = useInfiniteQuery({
    queryKey: ["laws", bounds.from, bounds.to],
    queryFn: ({ pageParam }) =>
      fetchLawPage({ ...bounds, cursor: pageParam, limit: LAW_PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    initialData: { pages: [firstPage], pageParams: [undefined] },
    staleTime: Infinity,
  });
  const items = query.data.pages.flatMap((page) => page.items ?? []);

  return (
    <>
      <nav aria-label="기간" className="mb-3 flex gap-1.5">
        {RANGE_KEYS.map((key) => (
          <Link
            key={key}
            href={`/laws?range=${key}`}
            aria-current={key === range ? "page" : undefined}
            className={cn(
              "rounded-full border px-3 py-1 text-[12.5px]",
              key === range
                ? "border-navy bg-navy font-bold text-white"
                : "border-border bg-white",
            )}
          >
            {RANGES[key].label}
          </Link>
        ))}
      </nav>
      {items.length === 0 ? (
        <p className="rounded-lg border border-border bg-card p-3.5 text-sm text-muted-foreground">
          이 기간에 시행되는 법령이 없어요.
        </p>
      ) : (
        <ul className="divide-y divide-line-soft rounded-lg border border-border bg-card px-3.5">
          {items.map((item) => (
            <LawRow
              key={`${item.lawId}@${item.effectiveDate}`}
              item={item}
              today={today}
              detailed
            />
          ))}
        </ul>
      )}
      {query.hasNextPage && (
        <div className="mt-3 text-center">
          {query.isFetchNextPageError && (
            <p role="alert" className="mb-1 text-sm text-removed">
              더 불러오지 못했어요.
            </p>
          )}
          <button
            type="button"
            onClick={() => query.fetchNextPage()}
            disabled={query.isFetchingNextPage}
            className="rounded-md border border-border bg-white px-4 py-1.5 text-[13px] font-bold"
          >
            {query.isFetchingNextPage
              ? "불러오는 중…"
              : query.isFetchNextPageError
                ? "다시 시도"
                : "더 보기"}
          </button>
        </div>
      )}
    </>
  );
}
