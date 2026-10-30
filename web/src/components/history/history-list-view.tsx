"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { SessionExpiredDialog } from "@/components/auth/session-expired-dialog";
import { ConfirmDialog } from "@/components/me/confirm-dialog";
import { isUnauthorized } from "@/lib/api/errors";
import { deleteHistory, fetchHistory, HISTORY_KEY } from "@/lib/api/history";
import type { HistoryItem } from "@/lib/api/types";
import { seoulDate } from "@/lib/dates";
import { useRequireSession } from "@/lib/session";

type ShownHistory = Required<
  Pick<HistoryItem, "id" | "query" | "lawTitle" | "savedAt">
> &
  HistoryItem;

function isShown(item: HistoryItem): item is ShownHistory {
  return (
    item.id !== undefined && !!item.query && !!item.lawTitle && !!item.savedAt
  );
}

/** S13 이력 목록(D73) — 저장 최근 순, 법령 갱신됨 배지, 삭제는 확인 후(스냅샷은 다시 만들 수 없다). */
export function HistoryListView() {
  const session = useRequireSession();
  const signedIn = session.isSuccess && session.data !== null;
  const queryClient = useQueryClient();
  const history = useQuery({
    queryKey: HISTORY_KEY,
    queryFn: fetchHistory,
    enabled: signedIn,
  });
  const [confirmFor, setConfirmFor] = useState<ShownHistory | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);
  const remove = useMutation({
    mutationFn: (id: number) => deleteHistory(id),
    onMutate: () => setMessage(null),
    onSuccess: () => {
      setConfirmFor(null);
      return queryClient.invalidateQueries({ queryKey: HISTORY_KEY });
    },
    onError: (error) => {
      setConfirmFor(null);
      if (isUnauthorized(error)) setExpired(true);
      else setMessage("이력을 지우지 못했어요");
    },
  });

  const items = history.data?.filter(isShown);

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-3 px-4 py-5">
      <h1 className="text-lg font-bold">이력</h1>
      {message && (
        <p role="alert" className="text-sm text-removed">
          {message}
        </p>
      )}
      {history.isError && (
        <p role="alert" className="text-sm text-removed">
          이력을 불러오지 못했어요
        </p>
      )}
      {items && items.length === 0 && (
        <p className="text-sm text-muted-foreground">
          결과 화면에서 &apos;이력에 저장&apos;을 누르면 여기에 모여요.
        </p>
      )}
      {items && items.length > 0 && (
        <ul
          aria-label="저장한 이력"
          className="divide-y divide-border rounded-lg border border-border bg-card px-3.5 text-sm"
        >
          {items.map((item) => (
            <li
              key={item.id}
              className="flex items-start justify-between gap-2 py-3"
            >
              <div className="min-w-0">
                <Link
                  href={`/me/history/${item.id}`}
                  className="line-clamp-2 font-bold"
                >
                  {item.query}
                </Link>
                <p className="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                  <span>
                    {item.lawTitle} · {seoulDate(new Date(item.savedAt))} 저장
                  </span>
                  {item.lawUpdated && (
                    <span className="rounded-sm bg-amend-bg px-1.5 py-0.5 font-bold text-amend">
                      법령 갱신됨
                    </span>
                  )}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setConfirmFor(item)}
                aria-label={`${item.query} 이력 삭제`}
                className="shrink-0 text-muted-foreground"
              >
                <span aria-hidden>🗑</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      <ConfirmDialog
        open={confirmFor !== null}
        title="이 기록을 지울까요?"
        description="지운 기록은 되돌릴 수 없어요."
        confirmLabel="삭제"
        danger
        confirmDisabled={remove.isPending}
        onConfirm={() => confirmFor && remove.mutate(confirmFor.id)}
        onClose={() => setConfirmFor(null)}
      />
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </div>
  );
}
