import { notFound, redirect } from "next/navigation";
import { Suspense } from "react";
import { fetchLawVersions } from "@/lib/api/laws";
import { lawHref } from "@/lib/law-view";

type LawVersionsPageProps = PageProps<"/laws/[lawId]">;

/** `/laws/{lawId}` — 가장 이른 시행예정본(S05)으로 보낸다. 없으면 not-found. */
export default function LawVersionsPage({ params }: LawVersionsPageProps) {
  return (
    <Suspense fallback={null}>
      <RedirectToEarliest params={params} />
    </Suspense>
  );
}

async function RedirectToEarliest({
  params,
}: Pick<LawVersionsPageProps, "params">) {
  const { lawId } = await params;
  const versions = await fetchLawVersions(lawId);
  const earliest = versions?.pending?.[0]?.effectiveDate;
  if (!earliest) notFound();
  return redirect(lawHref(lawId, earliest));
}
