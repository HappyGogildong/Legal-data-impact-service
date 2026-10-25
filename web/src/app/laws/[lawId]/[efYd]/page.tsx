import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { connection } from "next/server";
import { Suspense } from "react";
import { LawDetailView } from "@/components/laws/law-detail-view";
import {
  fetchDataStatusForPage,
  fetchLawDetail,
  fetchLawVersions,
} from "@/lib/api/laws";
import { seoulToday } from "@/lib/dates";
import { isIsoDate } from "@/lib/law-view";

type LawDetailPageProps = PageProps<"/laws/[lawId]/[efYd]">;

/** 검색 노출용 제목(법령명·시행일). 본문과 같은 캐시 조회라 백엔드를 두 번 부르지 않는다. */
export async function generateMetadata({
  params,
}: LawDetailPageProps): Promise<Metadata> {
  const { lawId, efYd } = await params;
  const detail = isIsoDate(efYd)
    ? await fetchLawDetail(lawId, efYd).catch(() => null)
    : null;
  return {
    title: detail?.title ? `${detail.title} (${efYd} 시행)` : "법령 상세",
  };
}

/** S05 법령 상세 — 서버 렌더링(검색 노출). 없는 버전·잘못된 날짜는 not-found, 백엔드 장애는 error(S16). */
export default function LawDetailPage({ params }: LawDetailPageProps) {
  return (
    <Suspense
      fallback={
        <p className="mx-auto max-w-[720px] px-4 py-5 text-sm text-muted-foreground">
          법령 정보를 불러오는 중이에요…
        </p>
      }
    >
      <LawDetailSection params={params} />
    </Suspense>
  );
}

async function LawDetailSection({
  params,
}: Pick<LawDetailPageProps, "params">) {
  const { lawId, efYd } = await params;
  if (!isIsoDate(efYd)) notFound();
  await connection();
  const today = seoulToday();
  const [detail, versions, dataStatus] = await Promise.all([
    fetchLawDetail(lawId, efYd),
    fetchLawVersions(lawId),
    fetchDataStatusForPage(),
  ]);
  if (!detail) notFound();
  return (
    <LawDetailView
      detail={detail}
      upcoming={versions?.pending ?? []}
      today={today}
      lawsUpdatedAt={dataStatus?.lawsUpdatedAt ?? null}
    />
  );
}
