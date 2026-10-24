"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchDataStatus } from "@/lib/api/analysis";
import { formatSeoulDateTime } from "@/lib/dates";

/** 데이터 현황 — 시각·개수만(service-api-spec §3.7). 브라우저에서 불러 빌드가 백엔드에 의존하지 않는다. */
export function DataStatusCard() {
  const status = useQuery({
    queryKey: ["data-status"],
    queryFn: fetchDataStatus,
    staleTime: 5 * 60_000,
  });
  const updatedAt = status.data?.lawsUpdatedAt;
  return (
    <section
      aria-labelledby="data-status-title"
      className="rounded-lg border border-border bg-card p-3.5 text-sm"
    >
      <h2 id="data-status-title" className="mb-1 font-bold">
        데이터 현황
      </h2>
      {status.isPending ? (
        <p className="text-muted-foreground">불러오는 중…</p>
      ) : status.isError ? (
        <p className="text-muted-foreground">지금은 현황을 불러올 수 없어요.</p>
      ) : (
        <dl>
          <div className="flex justify-between border-b border-line-soft py-2">
            <dt className="text-muted-foreground">법령 정보 갱신</dt>
            <dd className="font-bold">
              {updatedAt
                ? formatSeoulDateTime(updatedAt)
                : "아직 갱신 기록이 없어요"}
            </dd>
          </div>
          <div className="flex justify-between border-b border-line-soft py-2">
            <dt className="text-muted-foreground">수록 시행예정 법령</dt>
            <dd className="font-bold">{status.data.pendingLawCount ?? 0}건</dd>
          </div>
          <div className="flex justify-between py-2">
            <dt className="text-muted-foreground">출처</dt>
            <dd>국가법령정보센터 · 매일 갱신</dd>
          </div>
        </dl>
      )}
    </section>
  );
}
