import Link from "next/link";
import { ChangedArticles } from "@/components/analysis/changed-articles";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import type { LawDetail, UpcomingVersion } from "@/lib/api/types";
import { formatSeoulDateTime } from "@/lib/dates";
import { lawHref, safeExternalUrl, statusNotice } from "@/lib/law-view";
import { cn } from "@/lib/utils";
import { DDayBadge } from "./d-day-badge";
import { AskAboutLaw, SummaryButton } from "./law-actions";

type LawDetailViewProps = {
  detail: LawDetail;
  /** 같은 법령의 시행예정본(시행일 선택, D43) */
  upcoming: UpcomingVersion[];
  /** 서울 오늘 "YYYY-MM-DD" */
  today: string;
  lawsUpdatedAt: string | null;
};

/** S05 법령 상세 — 정본에서 결정론으로 만든 사실만(요약은 버튼 → S02, D65). 서버 렌더링, 접기·버튼만 브라우저. */
export function LawDetailView({
  detail,
  upcoming,
  today,
  lawsUpdatedAt,
}: LawDetailViewProps) {
  const pending = detail.status === "시행예정";
  const notice = statusNotice(detail.status, detail.effectiveDate);
  const lawId = detail.lawId;
  const lawRef =
    lawId && detail.effectiveDate
      ? { lawId, effectiveDate: detail.effectiveDate }
      : null;
  const title = detail.title ?? `법령 ${lawId ?? ""}`;
  const promulgation =
    detail.promulgateDate &&
    `공포 ${detail.promulgateDate}${detail.promulgateNo ? ` 제${detail.promulgateNo}호` : ""}`;
  const meta = [detail.lawType, detail.ministry, promulgation]
    .filter(Boolean)
    .join(" · ");
  const sourceUrl = safeExternalUrl(detail.sourceUrl);
  const changed = detail.changedArticles ?? [];
  const addenda = detail.addenda ?? [];
  const delegations = detail.delegationClauses ?? [];

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-3 px-4 py-5">
      <header className="rounded-lg border border-border bg-card p-3.5">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-lg font-bold">{title}</h1>
          {pending && detail.effectiveDate && (
            <DDayBadge effectiveDate={detail.effectiveDate} today={today} />
          )}
        </div>
        {meta && <p className="mt-0.5 text-xs text-muted-foreground">{meta}</p>}
        {detail.effectiveDate && (
          <p className="mt-0.5 text-xs text-muted-foreground">
            {detail.effectiveDate} 시행
          </p>
        )}
        {lawId && upcoming.length > 1 && (
          <nav
            aria-label="시행일"
            className="mt-2 flex flex-wrap items-center gap-1.5 text-xs"
          >
            {upcoming.map(
              (version) =>
                version.effectiveDate && (
                  <Link
                    key={version.effectiveDate}
                    href={lawHref(lawId, version.effectiveDate)}
                    aria-current={
                      version.effectiveDate === detail.effectiveDate
                        ? "page"
                        : undefined
                    }
                    className={cn(
                      "rounded-full border px-2.5 py-1",
                      version.effectiveDate === detail.effectiveDate
                        ? "border-navy font-bold text-navy"
                        : "border-border bg-white",
                    )}
                  >
                    {version.effectiveDate}
                  </Link>
                ),
            )}
          </nav>
        )}
        {sourceUrl && (
          <a
            href={sourceUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-2 inline-block text-xs font-bold text-navy"
          >
            국가법령정보센터 원문 ↗
          </a>
        )}
      </header>

      {notice && (
        <div
          role="status"
          className="rounded-lg border border-amend/40 bg-amend-bg p-3 text-sm"
        >
          <p className="font-bold">{notice.title}</p>
          <p className="text-ink">{notice.body}</p>
        </div>
      )}

      <section
        aria-labelledby="law-summary-title"
        className="rounded-lg border border-border bg-card p-3.5"
      >
        <h2 id="law-summary-title" className="mb-1 font-bold">
          {pending ? "쉬운 요약" : "개정 이유"}
        </h2>
        {pending && lawRef && (
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <SummaryButton title={title} lawRef={lawRef} />
            <span className="text-xs text-muted-foreground">
              근거 조문과 함께 쉽게 풀어 드려요.
            </span>
          </div>
        )}
        {pending && (
          <h3 className="mt-2 text-xs font-bold text-muted-foreground">
            개정 이유
          </h3>
        )}
        <p className="text-sm whitespace-pre-line">
          {detail.amendReason || "개정 이유가 제공되지 않았어요."}
        </p>
      </section>

      <section
        aria-labelledby="law-enforcement-title"
        className="rounded-lg border border-border bg-card p-3.5"
      >
        <div className="mb-1 flex items-center gap-2">
          <h2 id="law-enforcement-title" className="font-bold">
            시행 규칙
          </h2>
          {detail.enforcementType && (
            <span className="rounded-sm bg-chip px-1.5 py-0.5 text-[11px] font-bold text-navy">
              {detail.enforcementType}
            </span>
          )}
        </div>
        <blockquote className="border-l-2 border-line-soft pl-2.5 text-sm whitespace-pre-line">
          {detail.effectiveClause?.text ??
            detail.effectiveRule ??
            "시행 규칙이 제공되지 않았어요."}
        </blockquote>
        {detail.effectiveClause?.label && (
          <p className="mt-1 text-xs text-muted-foreground">
            {detail.effectiveClause.label}
          </p>
        )}
      </section>

      <section
        aria-labelledby="law-changed-title"
        className="rounded-lg border border-border bg-card p-3.5"
      >
        <h2 id="law-changed-title" className="mb-1 font-bold">
          바뀐 조문 {changed.length}개
        </h2>
        {changed.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            이번 개정에서 바뀐 조문 정보가 없어요.
          </p>
        ) : (
          <ChangedArticles
            articles={changed}
            title={null}
            anchored
            compare={pending}
          />
        )}
      </section>

      {addenda.length > 0 && (
        <Collapsible className="rounded-lg border border-border bg-card p-3.5">
          <CollapsibleTrigger className="flex w-full items-center justify-between text-left font-bold">
            <span>
              부칙{" "}
              <span className="text-xs font-normal text-muted-foreground">
                이번 개정분 {addenda.length}개
              </span>
            </span>
            <span aria-hidden>▾</span>
          </CollapsibleTrigger>
          <CollapsibleContent className="mt-2 flex flex-col gap-2 text-sm">
            {addenda.map((addendum) => (
              <div key={addendum.sourceId ?? addendum.label}>
                <p className="font-bold">{addendum.label}</p>
                <p className="whitespace-pre-line">{addendum.text}</p>
              </div>
            ))}
          </CollapsibleContent>
        </Collapsible>
      )}

      {delegations.length > 0 && (
        <Collapsible className="rounded-lg border border-border bg-card p-3.5">
          <CollapsibleTrigger className="flex w-full items-center justify-between text-left font-bold">
            <span>위임 조항 {delegations.length}개</span>
            <span aria-hidden>▾</span>
          </CollapsibleTrigger>
          <CollapsibleContent className="mt-2 text-sm">
            <p className="mb-1 text-xs text-muted-foreground">
              &ldquo;대통령령으로 정하는 사항&rdquo; — 하위 법령은 아직 정해지지
              않았을 수 있어요.
            </p>
            <ul className="list-disc pl-4">
              {delegations.map((clause, i) => (
                <li key={i}>{clause}</li>
              ))}
            </ul>
          </CollapsibleContent>
        </Collapsible>
      )}

      {pending && lawRef && (
        <section aria-labelledby="law-ask-title">
          <h2 id="law-ask-title" className="mb-1 font-bold">
            이 법령에 대해 질문하기
          </h2>
          <AskAboutLaw
            law={{
              lawId: lawRef.lawId,
              effectiveDate: lawRef.effectiveDate,
              title,
            }}
          />
        </section>
      )}

      <footer className="text-[11.5px] text-muted-foreground">
        {lawsUpdatedAt && (
          <>법령 정보 {formatSeoulDateTime(lawsUpdatedAt)} 갱신 · </>
        )}
        출처 국가법령정보센터 · 법률 자문이 아닌 참고 정보예요.
      </footer>
    </div>
  );
}
