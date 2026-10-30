"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { LawHeaderView } from "@/components/analysis/law-header-view";
import { ResultBody } from "@/components/analysis/result-body";
import { SessionExpiredDialog } from "@/components/auth/session-expired-dialog";
import { ConfirmDialog } from "@/components/me/confirm-dialog";
import { isUnauthorized } from "@/lib/api/errors";
import {
  deleteHistory,
  fetchHistoryDetail,
  HISTORY_KEY,
} from "@/lib/api/history";
import { setAsk } from "@/lib/ask-store";
import { seoulDate } from "@/lib/dates";
import { lawHref } from "@/lib/law-view";
import { useRequireSession } from "@/lib/session";

const noAlternative = () => {};

/**
 * S13 이력 상세(D73) — 저장 당시 결과 그대로(S02 본문을 읽기 전용으로) + "저장 당시 기준" 배너.
 * 법령이 바뀌었으면 같은 질문으로 최신 시행예정본을 다시 분석하거나(없으면) 법령 상세로.
 */
export function HistoryDetailView() {
  const { id } = useParams<{ id: string }>();
  const validId = /^\d+$/.test(id) ? Number(id) : null;
  const session = useRequireSession();
  const signedIn = session.isSuccess && session.data !== null;
  const router = useRouter();
  const queryClient = useQueryClient();
  const detail = useQuery({
    queryKey: [...HISTORY_KEY, id],
    queryFn: () => fetchHistoryDetail(validId!),
    enabled: signedIn && validId !== null,
  });
  const [confirm, setConfirm] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);
  const remove = useMutation({
    mutationFn: () => deleteHistory(validId!),
    onMutate: () => setMessage(null),
    onSuccess: async () => {
      // 마운트된 상세 쿼리를 다시 받으면 404 → "찾는 기록이 없어요"가 깜빡인다. 목록은 오래된 것으로 표시만 하고 열릴 때 다시 받는다.
      await queryClient.invalidateQueries({
        queryKey: HISTORY_KEY,
        refetchType: "none",
      });
      router.push("/me/history");
    },
    onError: (error) => {
      setConfirm(false);
      if (isUnauthorized(error)) setExpired(true);
      else setMessage("이력을 지우지 못했어요");
    },
  });

  if (validId === null || detail.data === null) {
    return (
      <div className="mx-auto flex max-w-[720px] flex-col gap-2 px-4 py-10 text-center text-sm">
        <p className="font-bold">찾는 기록이 없어요</p>
        <Link href="/me/history" className="font-bold text-navy">
          이력 목록
        </Link>
      </div>
    );
  }
  if (detail.isError) {
    return (
      <p role="alert" className="px-4 py-10 text-center text-sm text-removed">
        이력을 불러오지 못했어요
      </p>
    );
  }
  const data = detail.data;
  const law = data?.body?.law;
  if (!data || !data.body || !law) return null;

  const savedDay = data.savedAt ? seoulDate(new Date(data.savedAt)) : "";
  const answers = data.answers ?? [];
  const reanalyze = () => {
    if (!data.query || !data.lawId || !data.latestEffectiveDate) return;
    setAsk({
      query: data.query,
      lawRef: { lawId: data.lawId, effectiveDate: data.latestEffectiveDate },
    });
    router.push("/ask");
  };

  return (
    <div className="flex flex-col">
      <div className="mx-auto w-full max-w-[1120px] px-4 pt-4">
        <div className="rounded-lg border border-border bg-chip/40 p-3 text-sm">
          <p className="font-bold">저장 당시({savedDay}) 기준이에요</p>
          {data.lawUpdated && (
            <div className="mt-1 flex flex-wrap items-center gap-2">
              <span>그 뒤 법령이 바뀌었어요</span>
              {data.latestEffectiveDate ? (
                <button
                  type="button"
                  onClick={reanalyze}
                  className="rounded-md bg-navy px-3 py-1 text-[12.5px] font-bold text-white"
                >
                  최신 내용으로 다시 분석
                </button>
              ) : (
                data.lawId &&
                data.effectiveDate && (
                  <Link
                    href={lawHref(data.lawId, data.effectiveDate)}
                    className="text-[12.5px] font-bold text-navy"
                  >
                    법령 상세 보기
                  </Link>
                )
              )}
            </div>
          )}
        </div>
        {message && (
          <p role="alert" className="mt-2 text-sm text-removed">
            {message}
          </p>
        )}
      </div>
      <ResultBody
        response={data.body}
        revision={data.revision}
        header={
          <LawHeaderView
            law={law}
            alternatives={[]}
            onPickAlternative={noAlternative}
          />
        }
        afterCards={() =>
          answers.length > 0 && (
            <section
              aria-label="확인한 상황"
              className="rounded-lg border border-border bg-card p-3.5 text-sm"
            >
              <h2 className="font-bold">확인한 상황</h2>
              <ul className="mt-1.5 flex flex-wrap gap-1.5">
                {answers.map((answer, index) => (
                  <li
                    key={index}
                    className="rounded-full bg-chip px-2.5 py-1 text-xs"
                  >
                    {answer.question}: {answer.answer}
                  </li>
                ))}
              </ul>
            </section>
          )
        }
      />
      <div className="mx-auto w-full max-w-[1120px] px-4 pb-6">
        <button
          type="button"
          onClick={() => setConfirm(true)}
          className="rounded-md border border-removed/40 bg-white px-3 py-1.5 text-[12.5px] font-bold text-removed"
        >
          삭제
        </button>
      </div>
      <ConfirmDialog
        open={confirm}
        title="이 기록을 지울까요?"
        description="지운 기록은 되돌릴 수 없어요."
        confirmLabel="삭제"
        danger
        confirmDisabled={remove.isPending}
        onConfirm={() => remove.mutate()}
        onClose={() => setConfirm(false)}
      />
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </div>
  );
}
