import { api } from "./client";
import { expectOk, unwrap } from "./errors";
import type { BookmarkItem } from "./types";

/** 북마크 목록 쿼리 키 — ☆ 상태(S02·S05)와 S14 가 같은 목록을 쓴다(D72 ⑤). */
export const BOOKMARKS_KEY = ["bookmarks"] as const;

/** 내 관심 법령 — 추가한 시각 최근 순, 법령마다 보여 줄 버전. */
export async function fetchBookmarks(): Promise<BookmarkItem[]> {
  return unwrap(await api.GET("/api/v1/bookmarks")).items ?? [];
}

/** 멱등 추가. 한도 초과면 409 ApiError(메시지를 화면에 그대로). */
export async function addBookmark(lawId: string): Promise<void> {
  expectOk(
    await api.PUT("/api/v1/bookmarks/{lawId}", {
      params: { path: { lawId } },
    }),
  );
}

/** 멱등 해제. */
export async function removeBookmark(lawId: string): Promise<void> {
  expectOk(
    await api.DELETE("/api/v1/bookmarks/{lawId}", {
      params: { path: { lawId } },
    }),
  );
}
