"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId } from "react";
import { DDayBadge } from "@/components/laws/d-day-badge";
import type { DiscoveredLaw, Discovery } from "@/lib/api/types";
import { setDraft, type AskRequest } from "@/lib/ask-store";
import { seoulToday } from "@/lib/dates";
import { lawHref, sourceDetailHref } from "@/lib/law-view";
import { PersonalizeAction } from "./personalize-action";

/**
 * S04 법령 발견 결과(D70) — 법 이름 없이 찾은 시행예정 법령 후보. 카드 본문은 쉬운 요약 첫 문장이고
 * 원문은 근거 칩(→ S05 그 조문)으로만 잇는다. 고르면 원래 질문 + lawRef 로 다시 묻는다(S02).
 */
export function DiscoveryView({
  discovery,
  ask,
  onAsk,
}: {
  discovery: Discovery;
  ask: AskRequest;
  onAsk: (next: AskRequest) => void;
}) {
  const router = useRouter();
  const externalNoticeId = useId();
  const candidates = discovery.candidates ?? [];
  const profileSearch = ask.profileSearch ?? false;
  const today = seoulToday();

  const editQuestion = () => {
    setDraft(ask.query);
    router.push("/");
  };

  return (
    <div className="mx-auto max-w-[640px] px-4 py-6">
      <p className="text-sm text-muted-foreground">{ask.query}</p>
      <h1 className="mt-1 font-bold">
        질문과 관련 있어 보이는 시행예정 법령 {candidates.length}건
      </h1>

      <label className="mt-3 flex items-center gap-2 text-[13px]">
        <input
          type="checkbox"
          role="switch"
          aria-describedby={externalNoticeId}
          checked={profileSearch}
          onChange={(event) =>
            onAsk({ query: ask.query, profileSearch: event.target.checked })
          }
        />
        내 프로필 기준으로 찾기
      </label>
      <p id={externalNoticeId} className="text-xs text-muted-foreground">
        켜면 프로필 항목이 검색 처리를 위해 외부 서비스로 전송돼요
      </p>
      {discovery.profileBlocked && (
        <div className="mt-2 rounded-lg border border-border bg-card p-3 text-[13px]">
          <PersonalizeAction unmet={discovery.profileBlocked} />
        </div>
      )}

      {candidates.length === 0 ? (
        <div className="py-10 text-center">
          <p className="font-bold">관련 시행예정 법령을 찾지 못했어요</p>
          <p className="text-sm text-muted-foreground">
            바뀌는 내용이나 대상을 넣어 다시 물어봐 주세요. 예) 전세 세입자에게
            바뀌는 법 있어?
          </p>
          <button
            type="button"
            onClick={editQuestion}
            className="mt-3 text-[12.5px] font-bold text-navy"
          >
            질문 고치기
          </button>
        </div>
      ) : (
        <ul className="mt-4 flex flex-col gap-3">
          {candidates.map((law) => (
            <CandidateCard
              key={`${law.lawId}@${law.effectiveDate}`}
              law={law}
              today={today}
              onAnalyze={() =>
                onAsk({
                  query: ask.query,
                  lawRef: {
                    lawId: law.lawId!,
                    effectiveDate: law.effectiveDate!,
                  },
                })
              }
            />
          ))}
        </ul>
      )}
    </div>
  );
}

function CandidateCard({
  law,
  today,
  onAnalyze,
}: {
  law: DiscoveredLaw;
  today: string;
  onAnalyze: () => void;
}) {
  if (!law.lawId || !law.effectiveDate) return null;
  const detail = lawHref(law.lawId, law.effectiveDate);
  const chipHref = law.matched?.sourceId
    ? (sourceDetailHref(law.matched.sourceId) ?? detail)
    : detail;
  return (
    <li className="rounded-lg border border-border bg-card p-3.5 text-[13px]">
      <div className="flex items-center justify-between gap-2">
        <p className="font-bold">{law.title ?? `법령 ${law.lawId}`}</p>
        <DDayBadge effectiveDate={law.effectiveDate} today={today} />
      </div>
      {law.ministry && (
        <p className="text-xs text-muted-foreground">{law.ministry}</p>
      )}
      {law.summary && <p className="mt-1.5">{law.summary}</p>}
      {!law.summaryReady && (
        <p className="text-xs text-muted-foreground">요약 준비 중</p>
      )}
      {law.matched?.label && (
        <Link
          href={chipHref}
          className="mt-1.5 inline-block rounded-sm bg-chip px-1.5 py-0.5 text-[11px] font-bold text-navy"
        >
          {law.matched.label}
        </Link>
      )}
      <div className="mt-2 flex gap-2">
        <button
          type="button"
          onClick={onAnalyze}
          className="rounded-md bg-navy px-3 py-1.5 text-[12.5px] font-bold text-white"
        >
          이 법령으로 분석
        </button>
        <Link
          href={detail}
          className="rounded-md border border-border px-3 py-1.5 text-[12.5px] font-bold"
        >
          상세 보기
        </Link>
      </div>
    </li>
  );
}
