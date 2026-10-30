"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { SessionExpiredDialog } from "@/components/auth/session-expired-dialog";
import { DDayBadge } from "@/components/laws/d-day-badge";
import { useToday } from "@/hooks/use-browser";
import {
  addBookmark,
  BOOKMARKS_KEY,
  removeBookmark,
} from "@/lib/api/bookmarks";
import { ApiError, isUnauthorized } from "@/lib/api/errors";
import type { BookmarkItem } from "@/lib/api/types";
import { lawHref } from "@/lib/law-view";
import { useRequireSession } from "@/lib/session";
import { useBookmarks } from "./use-bookmarks";

type ShownBookmark = Required<
  Pick<BookmarkItem, "lawId" | "title" | "status" | "effectiveDate">
>;

function isShown(item: BookmarkItem): item is BookmarkItem & ShownBookmark {
  return !!(item.lawId && item.title && item.status && item.effectiveDate);
}

/** S14 관심 법령(D72) — 보관만, 알림 문구 없음. 해제는 바로, "되돌리기"는 화면을 떠날 때까지. */
export function BookmarkListView() {
  const session = useRequireSession();
  const signedIn = session.isSuccess && session.data !== null;
  const bookmarks = useBookmarks(signedIn);
  const queryClient = useQueryClient();
  const today = useToday();
  const [removed, setRemoved] = useState<ReadonlySet<string>>(new Set());
  const [message, setMessage] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);

  const removedRef = useRef<ReadonlySet<string>>(removed);
  useEffect(() => {
    removedRef.current = removed;
  }, [removed]);

  // 여기서 해제한 행은 되돌리기 자리로 캐시에 남겨 두고, 떠날 때 캐시에서 빼고 무효화해 S02·S05 의 ☆ 에 반영한다
  useEffect(
    () => () => {
      const gone = removedRef.current;
      if (gone.size === 0) return;
      queryClient.setQueryData<BookmarkItem[]>(BOOKMARKS_KEY, (old) =>
        old?.filter((item) => !gone.has(item.lawId ?? "")),
      );
      void queryClient.invalidateQueries({ queryKey: BOOKMARKS_KEY });
    },
    [queryClient],
  );

  function fail(error: unknown) {
    if (isUnauthorized(error)) setExpired(true);
    else
      setMessage(
        error instanceof ApiError && error.status === 409
          ? error.message
          : "북마크를 바꾸지 못했어요",
      );
  }
  const remove = useMutation({
    mutationFn: removeBookmark,
    onMutate: () => setMessage(null),
    onSuccess: (_, lawId) => setRemoved((prev) => new Set(prev).add(lawId)),
    onError: fail,
  });
  const restore = useMutation({
    mutationFn: addBookmark,
    onMutate: () => setMessage(null),
    onSuccess: (_, lawId) =>
      setRemoved((prev) => {
        const next = new Set(prev);
        next.delete(lawId);
        return next;
      }),
    onError: fail,
  });

  const items = bookmarks.data?.filter(isShown);

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-3 px-4 py-5">
      <h1 className="text-lg font-bold">북마크</h1>
      {message && (
        <p role="alert" className="text-sm text-removed">
          {message}
        </p>
      )}
      {bookmarks.isError && (
        <p role="alert" className="text-sm text-removed">
          북마크를 불러오지 못했어요
        </p>
      )}
      {items && items.length === 0 && (
        <p className="text-sm text-muted-foreground">
          관심 법령의 ☆를 누르면 여기에 모여요.{" "}
          <Link href="/" className="font-bold text-navy">
            홈으로
          </Link>
        </p>
      )}
      {items && items.length > 0 && (
        <ul
          aria-label="북마크한 법령"
          className="divide-y divide-border rounded-lg border border-border bg-card px-3.5 text-sm"
        >
          {items.map((item) =>
            removed.has(item.lawId) ? (
              <li
                key={item.lawId}
                className="flex items-center justify-between gap-2 py-3"
              >
                <span className="text-muted-foreground">
                  북마크를 해제했어요
                </span>
                <button
                  type="button"
                  onClick={() => restore.mutate(item.lawId)}
                  disabled={restore.isPending}
                  className="text-[12.5px] font-bold text-navy"
                >
                  되돌리기
                </button>
              </li>
            ) : (
              <li
                key={item.lawId}
                className="flex items-center justify-between gap-2 py-3"
              >
                <div className="flex min-w-0 flex-wrap items-center gap-1.5">
                  <Link
                    href={lawHref(item.lawId, item.effectiveDate)}
                    className="font-bold"
                  >
                    {item.title}
                  </Link>
                  <StatusBadge item={item} today={today} />
                </div>
                <button
                  type="button"
                  onClick={() => remove.mutate(item.lawId)}
                  disabled={remove.isPending}
                  aria-label={`${item.title} 북마크 해제`}
                  className="text-lg leading-none text-navy"
                >
                  <span aria-hidden>★</span>
                </button>
              </li>
            ),
          )}
        </ul>
      )}
      <SessionExpiredDialog open={expired} onClose={() => setExpired(false)} />
    </div>
  );
}

function StatusBadge({
  item,
  today,
}: {
  item: ShownBookmark;
  today: string | null;
}) {
  if (item.status === "시행예정")
    return (
      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
        {today && (
          <DDayBadge effectiveDate={item.effectiveDate} today={today} />
        )}{" "}
        시행예정
      </span>
    );
  if (item.status === "시행됨")
    return (
      <span className="text-xs text-muted-foreground">
        시행됨 {item.effectiveDate}
      </span>
    );
  return <span className="text-xs text-removed">시행 예정이던 개정 철회</span>;
}
